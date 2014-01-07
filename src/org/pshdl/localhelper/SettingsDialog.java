package org.pshdl.localhelper;

import java.io.*;

import org.eclipse.swt.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.pshdl.localhelper.PSSyncCommandLine.Configuration;

public class SettingsDialog {
	private final Configuration config;
	private Text fpgaProgrammerLocation;
	private Text synplifyLocation;
	private Text actTCLShellLocation;
	private Label lblFpgaprogrammerExecutable;
	private Label lblSerialPort;
	private Label lblSynplify;
	private Label lblActelTclShell;
	private Combo comPortBox;

	public SettingsDialog(Configuration config) {
		this.config = config;
	}

	/**
	 * @wbp.parser.entryPoint
	 */
	public Shell createShell() {
		final Shell shell = new Shell(Display.getCurrent());
		shell.setLayout(new GridLayout(3, false));

		lblFpgaprogrammerExecutable = new Label(shell, SWT.NONE);
		lblFpgaprogrammerExecutable.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblFpgaprogrammerExecutable.setText("fpga_programmer executable");

		fpgaProgrammerLocation = new Text(shell, SWT.BORDER);
		fpgaProgrammerLocation.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

		final Button btnLocate = new Button(shell, SWT.NONE);
		btnLocate.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
		btnLocate.setText("locate");

		lblSerialPort = new Label(shell, SWT.NONE);
		lblSerialPort.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblSerialPort.setText("serial port");

		comPortBox = new Combo(shell, SWT.NONE);
		comPortBox.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

		final Button btnUpdateList = new Button(shell, SWT.NONE);
		btnUpdateList.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
		btnUpdateList.setText("update list");

		lblSynplify = new Label(shell, SWT.NONE);
		lblSynplify.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblSynplify.setText("synplify executable");

		synplifyLocation = new Text(shell, SWT.BORDER);
		synplifyLocation.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

		final Button btnLocate_1 = new Button(shell, SWT.NONE);
		btnLocate_1.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
		btnLocate_1.setText("locate");

		lblActelTclShell = new Label(shell, SWT.NONE);
		lblActelTclShell.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblActelTclShell.setText("Actel TCL Shell executable");

		actTCLShellLocation = new Text(shell, SWT.BORDER);
		actTCLShellLocation.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

		final Button btnLocate_2 = new Button(shell, SWT.NONE);
		btnLocate_2.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
		btnLocate_2.setText("locate");
		new Label(shell, SWT.NONE);
		new Label(shell, SWT.NONE);

		final Button btnSaveSettings = new Button(shell, SWT.NONE);
		btnSaveSettings.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
		btnSaveSettings.setText("Save settings");
		loadFromSettings();
		return shell;
	}

	public void validateSettings() {
		updateLabel(new File(synplifyLocation.getText()), lblSynplify);
		updateLabel(new File(actTCLShellLocation.getText()), lblActelTclShell);
		updateLabel(new File(fpgaProgrammerLocation.getText()), lblFpgaprogrammerExecutable);
	}

	private void updateLabel(File file, Label label) {
		if (file.exists()) {
			label.setForeground(Display.getCurrent().getSystemColor(SWT.COLOR_RED));
		} else {
			label.setForeground(null);
		}
	}

	private void loadFromSettings() {
		actTCLShellLocation.setText(config.acttclsh.getAbsolutePath());
		synplifyLocation.setText(config.synplify.getAbsolutePath());
		fpgaProgrammerLocation.setText(config.progammer.getAbsolutePath());
		validateSettings();
	}

}
