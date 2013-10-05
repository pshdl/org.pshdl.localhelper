package org.pshdl.localhelper;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.prefs.*;

import javax.ws.rs.client.*;
import javax.ws.rs.core.*;

import org.glassfish.jersey.client.*;
import org.pshdl.rest.models.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.google.common.base.*;
import com.google.common.io.*;

public class WorkspaceHelper {
	public static enum Status {
		CONNECTING, CONNECTED, CLOSED, RECONNECT, ERROR
	}

	public static enum FileOp {
		ADDED, UPDATED, REMOVED;
	}

	public static enum Severity {
		INFO, WARNING, ERROR;
	}

	public static interface IWorkspaceListener {
		public void connectionStatus(Status status);

		public void doLog(Severity severity, String message);

		public void incomingMessage(Message<?> message);

		public void fileOperation(FileOp op, File localFile);
	}

	private static final String PREF_LAST_WD = "WORKSPACE_DIR";
	private static final String WID_FILE = ".wid";
	private File root;
	private String workspaceID;
	private final Preferences prefs;
	private static final String SERVER = getServer();
	private static final ObjectWriter writer = JSONHelper.getWriter();
	private static final ObjectReader messageReader = JSONHelper.getReader(Message.class);
	private static final ObjectReader repoReader = JSONHelper.getReader(RepoInfo.class);
	private static final ObjectMapper mapper = JSONHelper.getMapper();
	private final IWorkspaceListener listener;
	private ChunkedInput<String> chunkedInput;

	public WorkspaceHelper(IWorkspaceListener listener) {
		this.prefs = Preferences.userNodeForPackage(this.getClass());
		final String string = prefs.get(PREF_LAST_WD, null);
		if (string != null) {
			root = new File(string);
			readWorkspaceID();
		}
		this.listener = listener;
	}

	public void readWorkspaceID() {
		final File widFile = new File(root, WID_FILE);
		if (widFile.exists()) {
			try {
				final String wid = Files.toString(widFile, Charsets.UTF_8);
				validateWorkspaceID(wid);
			} catch (final IOException e) {
			}
		}
	}

	public void setWorkspace(String folder) {
		this.root = new File(folder);
		prefs.put(PREF_LAST_WD, folder);
		readWorkspaceID();
	}

	private static String getServer() {
		final String property = System.getProperty("PSHDL_SERVER");
		if (property != null) {
			System.out.println("WorkspaceHelper.getServer()" + property);
			return property;
		}
		return "api.pshdl.org";
	}

	public String validateWorkspaceID(String wid) {
		wid = wid.trim();
		if (!wid.matches("[0-9a-fA-F]+"))
			return "The workspace ID should be a 16 digit hexadecimal number";
		if (wid.length() != 16)
			return "The workspace ID should be a 16 digit hexadecimal number";
		setWorkspaceID(wid);
		return null;
	}

	private void setWorkspaceID(String wid) {
		wid = wid.toUpperCase();
		workspaceID = wid;
		try {
			Files.write(wid, new File(root, WID_FILE), Charsets.UTF_8);
		} catch (final IOException e) {
			e.printStackTrace();
		}
	}

	public void connectTo(final String wid) throws IOException {
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					listener.connectionStatus(Status.CONNECTING);
					final ClientConfig clientConfig = new ClientConfig();
					final Client client = ClientBuilder.newClient(clientConfig);
					final WebTarget resource = client.target(getURL(wid, true));
					final String clientID = resource.path("clientID").request().get(String.class);
					connectToStream(wid, resource, clientID);
					final String repoInfo = client.target(getURL(wid, false)).request().accept(MediaType.APPLICATION_JSON).get(String.class);
					final RepoInfo repo = repoReader.readValue(repoInfo);
					for (final FileInfo fi : repo.getFiles()) {
						handleFileInfo(fi);
					}
					listener.connectionStatus(Status.CONNECTED);
				} catch (final Exception e) {
					listener.doLog(Severity.ERROR, e.getMessage());
					listener.connectionStatus(Status.ERROR);
				}
			}
		}, "connect").start();
	}

	private long lastConnect;
	private Thread chunky;

	public void connectToStream(final String wid, final WebTarget resource, final String clientID) {
		lastConnect = System.currentTimeMillis();
		final WebTarget path = resource.path(clientID).path("streaming");
		System.out.println("WorkspaceHelper.connectToStream()" + path.getUri());
		final Response response = path.request().get();
		chunkedInput = response.readEntity(new GenericType<ChunkedInput<String>>() {
		});
		chunky = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					String chunk;
					final ChunkedInput<String> ci = chunkedInput;
					while ((chunk = ci.read()) != null) {
						final Message<?> message = messageReader.readValue(chunk);
						listener.incomingMessage(message);
						handleMessage(message);
					}
					if (ci != chunkedInput)
						return;
					if ((System.currentTimeMillis() - lastConnect) > 1000) {
						listener.doLog(Severity.WARNING, "Connection closed, re-connecting");
						listener.connectionStatus(Status.RECONNECT);
						connectToStream(wid, resource, clientID);
					} else {
						listener.doLog(Severity.ERROR, "Connection closed, re-connecting too fast");
						listener.connectionStatus(Status.ERROR);
					}
				} catch (final Throwable e) {
					e.printStackTrace();
				}
			}
		}, "chunky");
		chunky.start();
	}

	protected void handleMessage(Message<?> message) {
		final String subject = message.getSubject();
		if (Message.ADDED.equals(subject)) {
			try {
				final FileInfo[] readValues = getContent(message, FileInfo[].class);
				for (final FileInfo fi : readValues) {
					handleFileInfo(fi);
				}
			} catch (final Exception e) {
				listener.doLog(Severity.ERROR, e.getMessage());
				e.printStackTrace();
			}
		}
		if (Message.UPDATED.equals(subject)) {
			try {
				final FileInfo[] readValues = getContent(message, FileInfo[].class);
				for (final FileInfo fi : readValues) {
					handleFileInfo(fi);
				}
			} catch (final Exception e) {
				listener.doLog(Severity.ERROR, e.getMessage());
				e.printStackTrace();
			}
		}
		if (Message.DELETED.equals(subject)) {
			try {
				final FileInfo fi = getContent(message, FileInfo.class);
				final File localFile = new File(root, fi.getName());
				if (localFile.exists()) {
					if (localFile.lastModified() > fi.getLastModified()) {
						listener.doLog(Severity.WARNING, "A file that existed locally is newer than a remotely deleted file:" + fi.getName());
					} else {
						localFile.delete();
						listener.fileOperation(FileOp.REMOVED, localFile);
					}
					final CompileInfo info = fi.getInfo();
					if (info != null) {
						deleteCompileInfoFiles(info);
					}
				} else {
					listener.doLog(Severity.WARNING, "A file that existed remotely but not locally has been deleted:" + fi.getName());
				}
			} catch (final Exception e) {
				listener.doLog(Severity.ERROR, e.getMessage());
				e.printStackTrace();
			}
		}
		if (Message.VHDL.equals(subject)) {
			System.out.println("WorkspaceHelper.handleMessage()" + message);
			try {
				final CompileContainer cc = getContent(message, CompileContainer.class);
				final Set<CompileInfo> results = cc.getCompileResults();
				for (final CompileInfo ci : results) {
					handleCompileInfo(ci);
				}
			} catch (final Exception e) {
				listener.doLog(Severity.ERROR, e.getMessage());
				e.printStackTrace();
			}
		}
	}

	private void deleteCompileInfoFiles(final CompileInfo info) {
		final File srcGenDir = new File(root, "src-gen/");
		final File file = new File(srcGenDir, info.getFile());
		file.delete();
		final List<OutputInfo> outputs = info.getAddOutputs();
		for (final OutputInfo oi : outputs) {
			final File oF = new File(srcGenDir, oi.getRelPath());
			deleteFileAndDir(srcGenDir, oF);
		}
	}

	private void deleteFileAndDir(File srcGenDir, File oF) {
		if (srcGenDir.getAbsolutePath().equals(oF.getAbsolutePath()))
			return;
		oF.delete();
		if (oF.getParentFile().list().length == 0) {
			deleteFileAndDir(srcGenDir, oF.getParentFile());
		}
	}

	public void handleFileInfo(final FileInfo fi) {
		final String name = fi.getName();
		final long lastModified = fi.getLastModified();
		handleFile(name, name, lastModified);
		final CompileInfo compileInfo = fi.getInfo();
		if (compileInfo != null) {
			handleCompileInfo(compileInfo);
		}
	}

	public void handleCompileInfo(final CompileInfo compileInfo) {
		final List<OutputInfo> addOutputs = compileInfo.getAddOutputs();
		for (final OutputInfo outputInfo : addOutputs) {
			handleFile("src-gen/" + outputInfo.getRelPath(), outputInfo.getFileURI(), outputInfo.getLastModified());
		}
		handleFile("src-gen/" + compileInfo.getFile(), compileInfo.getFileURI(), compileInfo.getCreated());
	}

	public void handleFile(final String name, String uri, final long lastModified) {
		final File localFile = new File(root, name);
		if (localFile.exists()) {
			if ((localFile.lastModified() < lastModified) || (lastModified == 0)) {
				downloadFile(localFile, FileOp.UPDATED, lastModified, uri);
			} else {
				if (localFile.lastModified() != lastModified) {
					localFile.renameTo(new File(localFile.getParent(), localFile.getName() + "_conflict" + localFile.lastModified()));
					listener.doLog(Severity.WARNING, "The remote file was older than the local file. Created a backup of local file and used remote file");
					downloadFile(localFile, FileOp.UPDATED, lastModified, uri);
				}
			}
		} else {
			final File parentFile = localFile.getParentFile();
			if (!parentFile.exists()) {
				parentFile.mkdirs();
			}
			downloadFile(localFile, FileOp.ADDED, lastModified, uri);
		}
	}

	public <T> T getContent(Message<?> message, Class<T> clazz) throws JsonProcessingException, IOException, JsonParseException, JsonMappingException {
		final Object json = message.getContents();
		final String jsonString = writer.writeValueAsString(json);
		return mapper.readValue(jsonString, clazz);
	}

	private void downloadFile(File localFile, FileOp op, long lastModified, String name) {
		try {
			URL url;
			if (name.charAt(0) != '/') {
				url = new URL(getURL(workspaceID, false) + "/" + name + "?plain=true");
			} else {
				url = new URL("http://" + getServer() + name + "?plain=true");
			}
			System.out.println("WorkspaceHelper.downloadFile()" + url);
			final InputStream os = url.openStream();
			ByteStreams.copy(os, Files.newOutputStreamSupplier(localFile));
			os.close();
			localFile.setLastModified(lastModified);
			listener.fileOperation(op, localFile);
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}

	public void closeConnection() {
		if (chunkedInput != null) {
			final ChunkedInput<?> ci = chunkedInput;
			final Thread t = chunky;
			chunky = null;
			chunkedInput = null;
			new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						t.interrupt();
						ci.close();
					} catch (final Exception e) {
						e.printStackTrace();
					}
				}
			}).start();
			listener.connectionStatus(Status.CLOSED);
		}
	}

	public boolean isConnected() {
		if (chunkedInput == null)
			return false;
		return !chunkedInput.isClosed();
	}

	public String getURL(String workspaceID, boolean streaming) {
		if (streaming)
			return "http://" + SERVER + "/api/v0.1/streaming/workspace/" + workspaceID.toUpperCase();
		return "http://" + SERVER + "/api/v0.1/workspace/" + workspaceID.toUpperCase();

	}

	public File getWorkspaceFolder() {
		return root;
	}

	public String getWorkspaceID() {
		return workspaceID;
	}

}
