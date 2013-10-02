package org.pshdl.localhelper;

import java.io.*;

import org.eclipse.swt.*;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.pshdl.localhelper.WorkspaceHelper.FileOp;
import org.pshdl.localhelper.WorkspaceHelper.IWorkspaceListener;
import org.pshdl.localhelper.WorkspaceHelper.Severity;
import org.pshdl.localhelper.WorkspaceHelper.Status;
import org.pshdl.rest.models.*;

public class GuiClient implements IWorkspaceListener {
	private final WorkspaceHelper helper;
	private Text widText;
	private Button btnConnect;
	private StyledText log;
	final Display display = new Display();
	private Shell shell;

	public static void main(String[] args) {
		final GuiClient client = new GuiClient();
		client.createUI();
		client.runUI();
		client.close();
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

	public GuiClient() {
		this.helper = new WorkspaceHelper(this);
	}

	private void close() {
		helper.closeConnection();
	}

	private Shell createUI() {
		shell = new Shell(display);
		shell.setSize(500, 400);
		shell.setLayout(new GridLayout(3, false));

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
				}

			}
		});
		btnChoose.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
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
					if ("disconnect".equals(btnConnect.getText())) {
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
		btnConnect.setText("Connect");

		final Label lblProgress = new Label(shell, SWT.NONE);
		lblProgress.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblProgress.setText("Progress");

		final ProgressBar progressBar = new ProgressBar(shell, SWT.NONE);
		progressBar.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false, 1, 1));
		new Label(shell, SWT.NONE);

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

		final Tray tray = display.getSystemTray();
		shell.open();

		if (tray == null) {
			System.out.println("The system tray is not available");
		} else {
			createTrayItem(shell, tray);
		}
		return shell;
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
		display.asyncExec(new Runnable() {

			@Override
			public void run() {
				System.out.println("GuiClient.connectionStatus(...).new Runnable() {...}.run()" + status);
			}
		});
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
}