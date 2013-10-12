package org.pshdl.localhelper;

import java.io.*;
import java.util.*;
import java.util.prefs.*;

import org.pshdl.rest.models.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.google.common.base.*;
import com.google.common.collect.*;
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

		public void doLog(Exception e);
	}

	public static interface MessageHandler<T> {
		public void handle(Message<T> msg, IWorkspaceListener listener) throws Exception;
	}

	public class FileInfoArrayHandler implements MessageHandler<FileInfo[]> {

		@Override
		public void handle(Message<FileInfo[]> msg, IWorkspaceListener listener) throws Exception {
			final FileInfo[] readValues = getContent(msg, FileInfo[].class);
			for (final FileInfo fi : readValues) {
				handleFileInfo(fi);
			}
		}

	}

	public class FileInfoDeleteHandler implements MessageHandler<FileInfo> {

		@Override
		public void handle(Message<FileInfo> msg, IWorkspaceListener listener) throws Exception {
			final FileInfo fi = getContent(msg, FileInfo.class);
			final File localFile = new File(root, fi.getRecord().getRelPath());
			if (localFile.exists()) {
				if (localFile.lastModified() > fi.getRecord().getLastModified()) {
					listener.doLog(Severity.WARNING, "A file that existed locally is newer than a remotely deleted file:" + fi.getRecord().getRelPath());
				} else {
					localFile.delete();
					listener.fileOperation(FileOp.REMOVED, localFile);
				}
				final CompileInfo info = fi.getInfo();
				if (info != null) {
					deleteCompileInfoFiles(info);
				}
			} else {
				listener.doLog(Severity.WARNING, "A file that existed remotely but not locally has been deleted:" + fi.getRecord().getRelPath());
			}

		}

	}

	public class CompileContainerHandler implements MessageHandler<CompileInfo[]> {

		@Override
		public void handle(Message<CompileInfo[]> msg, IWorkspaceListener listener) throws Exception {
			final CompileInfo[] cc = getContent(msg, CompileInfo[].class);
			for (final CompileInfo ci : cc) {
				handleCompileInfo(ci);
			}
		}

	}

	private static final String PREF_LAST_WD = "WORKSPACE_DIR";
	private static final String WID_FILE = ".wid";
	private File root;
	private String workspaceID;
	private final Preferences prefs;
	private static final ObjectWriter writer = JSONHelper.getWriter();
	private final IWorkspaceListener listener;
	private final ConnectionHelper ch;
	private final Multimap<String, MessageHandler<?>> handlerMap = LinkedListMultimap.create();

	private static final ObjectMapper mapper = JSONHelper.getMapper();

	public WorkspaceHelper(IWorkspaceListener listener) {
		this.prefs = Preferences.userNodeForPackage(this.getClass());
		final String string = prefs.get(PREF_LAST_WD, null);
		if (string != null) {
			setWorkspace(string);
		}
		this.listener = listener;
		this.ch = new ConnectionHelper(listener, this);
		registerFileSyncHandlers();
	}

	public void registerFileSyncHandlers() {
		handlerMap.put(Message.ADDED, new FileInfoArrayHandler());
		handlerMap.put(Message.UPDATED, new FileInfoArrayHandler());
		handlerMap.put(Message.DELETED, new FileInfoDeleteHandler());
		handlerMap.put(Message.VHDL, new CompileContainerHandler());
		handlerMap.put(Message.PSEX, new CompileContainerHandler());
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
		if (root.exists()) {

		}
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
			listener.doLog(e);
		}
	}

	protected <T> void handleMessage(Message<T> message) {
		final String subject = message.getSubject();
		final Iterable<String> split = Splitter.on(':').split(subject);
		final StringBuilder sb = new StringBuilder();
		for (final String string : split) {
			sb.append(string);
			final String newSubject = sb.toString();
			final Collection<MessageHandler<?>> handlers = handlerMap.get(newSubject);
			for (final MessageHandler<?> messageHandler : handlers) {
				@SuppressWarnings("unchecked")
				final MessageHandler<T> handler = (MessageHandler<T>) messageHandler;
				try {
					handler.handle(message, listener);
				} catch (final Exception e) {
					listener.doLog(e);
				}
			}
			sb.append(':');
		}
	}

	private void deleteCompileInfoFiles(final CompileInfo info) {
		final List<FileRecord> outputs = info.getFiles();
		for (final FileRecord oi : outputs) {
			final File oF = new File(root, oi.getRelPath());
			deleteFileAndDir(root, oF);
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
		handleFileUpdate(fi.getRecord());
		final CompileInfo compileInfo = fi.getInfo();
		if (compileInfo != null) {
			handleCompileInfo(compileInfo);
		}
	}

	public void handleCompileInfo(final CompileInfo compileInfo) {
		final List<FileRecord> addOutputs = compileInfo.getFiles();
		for (final FileRecord outputInfo : addOutputs) {
			handleFileUpdate(outputInfo);
		}
	}

	public void handleFileUpdate(FileRecord fr) {
		final File localFile = new File(root, fr.getRelPath());
		final long lastModified = fr.getLastModified() + ch.serverDiff;
		final String uri = fr.getFileURI();
		if (localFile.exists()) {
			long localLastModified = localFile.lastModified();
			if ((localLastModified < lastModified) || (lastModified == 0)) {
				ch.downloadFile(localFile, FileOp.UPDATED, lastModified, uri);
			} else {
				if (localLastModified != lastModified) {
					localFile.renameTo(new File(localFile.getParent(), localFile.getName() + "_conflict" + localLastModified));
					listener.doLog(Severity.WARNING, "The remote file was older than the local file. Created a backup of local file and used remote file");
					ch.downloadFile(localFile, FileOp.UPDATED, lastModified, uri);
				}
			}
		} else {
			final File parentFile = localFile.getParentFile();
			if (!parentFile.exists()) {
				parentFile.mkdirs();
			}
			ch.downloadFile(localFile, FileOp.ADDED, lastModified, uri);
		}
	}

	public static <T> T getContent(Message<?> message, Class<T> clazz) throws JsonProcessingException, IOException, JsonParseException, JsonMappingException {
		final Object json = message.getContents();
		final String jsonString = writer.writeValueAsString(json);
		return mapper.readValue(jsonString, clazz);
	}

	public File getWorkspaceFolder() {
		return root;
	}

	public String getWorkspaceID() {
		return workspaceID;
	}

	public void closeConnection() {
		ch.closeConnection();
	}

	public void connectTo(String wid) throws IOException {
		ch.connectTo(wid);
	}

}
