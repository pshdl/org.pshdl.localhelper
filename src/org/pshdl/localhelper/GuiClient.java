package org.pshdl.localhelper;

import java.io.*;
import java.util.prefs.*;

import org.eclipse.swt.*;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.pshdl.localhelper.ConnectionHelper.Status;
import org.pshdl.localhelper.PSSyncCommandLine.Configuration;
import org.pshdl.localhelper.WorkspaceHelper.FileOp;
import org.pshdl.localhelper.WorkspaceHelper.IWorkspaceListener;
import org.pshdl.localhelper.WorkspaceHelper.Severity;
import org.pshdl.rest.models.*;

public class GuiClient implements IWorkspaceListener {
	private static final String LAST_WD = "LAST_WD";
	private static final String CONNECTING_STR = "Connecting";
	private static final String CONNECT_STR = "Connect";
	private static final String DISCONNECT_STR = "Disconnect";
	private final WorkspaceHelper helper;
	private Text widText;
	private Button btnConnect;
	private StyledText log;
	final Display display = new Display();
	private Shell shell;
	private final Preferences pref;
	private final Configuration config;

	public static void main(String[] args) {
		try {
			final Configuration config = PSSyncCommandLine.configure(args);
			final GuiClient client = new GuiClient(config);
			client.createUI();
			client.runUI();
			client.close();
		} catch (final Exception e) {
			e.printStackTrace();
		}
		System.exit(0);
	}

	public void runUI() {
		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
		display.dispose();
	}

	public GuiClient(Configuration config) throws IOException {
		this.config = config;
		this.pref = Preferences.userNodeForPackage(GuiClient.class);
		final String lastWD = pref.get(LAST_WD, null);
		this.helper = new WorkspaceHelper(this, null, lastWD, config);
	}

	private void close() {
		helper.closeConnection();
	}

	private Shell createUI() {
		shell = new Shell(display);
		shell.setSize(500, 400);
		shell.setLayout(new GridLayout(3, false));
		loadImages();
		final Label lblWorkspaceDirectory = new Label(shell, SWT.NONE);
		lblWorkspaceDirectory.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblWorkspaceDirectory.setText("Workspace directory");

		final Label label = new Label(shell, SWT.NONE);
		label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
		final File workspaceFolder = helper.getWorkspaceFolder();
		if (workspaceFolder != null) {
			label.setText(workspaceFolder.getAbsolutePath());
		} else {
			label.setText("<Not selected>");
		}

		final Button btnChoose = new Button(shell, SWT.NONE);
		btnChoose.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				final DirectoryDialog dialog = new DirectoryDialog(shell, SWT.OPEN);
				final String folder = dialog.open();
				if (folder != null) {
					helper.setWorkspace(folder);
					label.setText(folder);
					widText.setEnabled(true);
					btnConnect.setEnabled(true);
					pref.put(LAST_WD, folder);
				}

			}
		});
		final GridData layoutData = new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1);
		layoutData.widthHint = 150;
		btnChoose.setLayoutData(layoutData);
		btnChoose.setText("Choose");

		final Label lblWorkspace = new Label(shell, SWT.NONE);
		lblWorkspace.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblWorkspace.setText("Workspace");

		widText = new Text(shell, SWT.BORDER);
		widText.setEnabled(workspaceFolder != null);
		final String wid = helper.getWorkspaceID();
		if (wid != null) {
			widText.setText(wid);
		}
		widText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

		btnConnect = new Button(shell, SWT.NONE);
		btnConnect.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				final String validate = helper.validateWorkspaceID(widText.getText());
				if (validate != null) {
					showAlert(shell, validate);
				} else {
					if (DISCONNECT_STR.equals(btnConnect.getText())) {
						helper.closeConnection();
					} else {
						try {
							helper.connectTo(widText.getText());
						} catch (final IOException e1) {
							e1.printStackTrace();
							showAlert(shell, e1.getMessage());
						}
					}
				}
			}

			public void showAlert(final Shell shlHelloWorldPlease, final String validate) {
				final MessageBox mb = new MessageBox(shlHelloWorldPlease, SWT.NONE);
				mb.setMessage(validate);
				mb.open();
			}
		});
		btnConnect.setEnabled(workspaceFolder != null);
		btnConnect.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
		btnConnect.setText(CONNECT_STR);

		final Label lblProgress = new Label(shell, SWT.NONE);
		lblProgress.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));

		final ProgressBar progressBar = new ProgressBar(shell, SWT.NONE);
		progressBar.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false, 1, 1));
		// new Label(shell, SWT.NONE);

		final Button settings = new Button(shell, SWT.NONE);
		settings.setText("Settings");
		settings.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
		settings.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				final SettingsDialog dialog = new SettingsDialog(config);
				dialog.createShell().open();
			}
		});

		final ScrolledComposite scrolledComposite = new ScrolledComposite(shell, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
		scrolledComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1));
		scrolledComposite.setExpandHorizontal(true);
		scrolledComposite.setExpandVertical(true);

		log = new StyledText(shell, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL | SWT.READ_ONLY);
		final GridData gd_styledText = new GridData(SWT.FILL, SWT.FILL, false, false, 3, 1);
		gd_styledText.heightHint = 100;
		gd_styledText.minimumHeight = 100;
		log.setLayoutData(gd_styledText);
		log.addListener(SWT.Modify, new Listener() {
			@Override
			public void handleEvent(Event e) {
				log.setTopIndex(log.getLineCount() - 1);
			}
		});

		// final Tray tray = display.getSystemTray();
		shell.pack();
		shell.open();

		// if (tray == null) {
		// System.out.println("The system tray is not available");
		// } else {
		// createTrayItem(shell, tray);
		// }
		return shell;
	}

	private void loadImages() {
		try {
			final ClassLoader loader = GuiClient.class.getClassLoader();
			final InputStream is16 = loader.getResourceAsStream("PSHDLIcon16.png");
			final Image icon16 = new Image(display, is16);
			is16.close();
			final InputStream is32 = loader.getResourceAsStream("PSHDLIcon32.png");
			final Image icon32 = new Image(display, is32);
			is32.close();
			final InputStream is64 = loader.getResourceAsStream("PSHDLIcon64.png");
			final Image icon64 = new Image(display, is64);
			is64.close();
			final InputStream is128 = loader.getResourceAsStream("PSHDLIcon128.png");
			final Image icon128 = new Image(display, is128);
			is128.close();
			shell.setImages(new Image[] { icon16, icon32, icon64, icon128 });
		} catch (final IOException e) {
			e.printStackTrace();
		}
	}

	private static void createTrayItem(final Shell shell, final Tray tray) {
		final Menu menu = createMenu(shell);
		final TrayItem item = new TrayItem(tray, SWT.NONE);
		item.setToolTipText("SWT TrayItem");
		item.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				showApp(shell);
			}
		});
		item.addListener(SWT.DefaultSelection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				showApp(shell);
			}
		});
		item.addListener(SWT.MenuDetect, new Listener() {
			@Override
			public void handleEvent(Event event) {
				menu.setVisible(true);
			}
		});
		final ImageLoader il = new ImageLoader();
		final InputStream img = GuiClient.class.getResourceAsStream("/PSHDLIcon128.png");
		final Image icon = new Image(shell.getDisplay(), il.load(img)[0]);
		item.setImage(icon);
		shell.addDisposeListener(new DisposeListener() {

			@Override
			public void widgetDisposed(DisposeEvent e) {
				icon.dispose();
			}
		});
	}

	private static Menu createMenu(final Shell shell) {
		final Menu menu = new Menu(shell, SWT.POP_UP);
		final MenuItem mi = new MenuItem(menu, SWT.NONE);
		mi.setText("Show Application");
		mi.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				showApp(shell);
			}
		});
		menu.setDefaultItem(mi);
		return menu;
	}

	private static void showApp(final Shell shell) {
		shell.setVisible(true);
		shell.setActive();
	}

	@Override
	public void connectionStatus(final Status status) {
		if ((display != null) && !display.isDisposed()) {
			if (status == Status.CONNECTED) {
				try {
					helper.announceServices();
				} catch (final IOException e) {
					e.printStackTrace();
				}
			}
			display.asyncExec(new Runnable() {

				@Override
				public void run() {
					switch (status) {
					case CLOSED:
					case ERROR:
						btnConnect.setEnabled(true);
						widText.setEnabled(true);
						btnConnect.setText(CONNECT_STR);
						break;
					case CONNECTING:
						btnConnect.setEnabled(false);
						widText.setEnabled(false);
						btnConnect.setText(CONNECTING_STR);
						break;
					case CONNECTED:
						btnConnect.setEnabled(true);
						widText.setEnabled(false);
						btnConnect.setText(DISCONNECT_STR);
						break;
					case RECONNECT:
						break;
					}
				}
			});
		}
	}

	@Override
	public void doLog(final Severity severity, final String message) {
		display.asyncExec(new Runnable() {
			@Override
			public void run() {
				final String logItem = severity + ": " + message + "\n";
				log.append(logItem);
			}
		});
	}

	@Override
	public void incomingMessage(Message<?> message) {
		doLog(Severity.INFO, "Received " + message);
	}

	@Override
	public void fileOperation(FileOp op, File localFile) {
		doLog(Severity.INFO, "Performed the file operation:" + op + " on file:" + localFile);
	}

	@Override
	public void doLog(Exception e) {
		doLog(Severity.ERROR, e.getMessage());
		e.printStackTrace();
	}
}