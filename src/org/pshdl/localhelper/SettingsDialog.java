/*******************************************************************************
 * PSHDL is a library and (trans-)compiler for PSHDL input. It generates
 *     output suitable for implementation or simulation of it.
 *
 *     Copyright (C) 2014 Karsten Becker (feedback (at) pshdl (dot) org)
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *     This License does not grant permission to use the trade names, trademarks,
 *     service marks, or product names of the Licensor, except as required for
 *     reasonable and customary use in describing the origin of the Work.
 *
 * Contributors:
 *     Karsten Becker - initial API and implementation
 ******************************************************************************/
package org.pshdl.localhelper;

import java.io.File;
import java.util.Arrays;
import java.util.prefs.Preferences;

import jssc.SerialPortList;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
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
	private final WorkspaceHelper helper;

	public SettingsDialog(Configuration config, WorkspaceHelper helper) {
		this.config = config;
		this.helper = helper;
	}

	/**
	 * wbp.parser.entryPoint
	 */
	public Shell createShell() {
		final Shell shell = new Shell(Display.getCurrent());
		shell.setLayout(new GridLayout(3, false));

		lblFpgaprogrammerExecutable = new Label(shell, SWT.NONE);
		lblFpgaprogrammerExecutable.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblFpgaprogrammerExecutable.setText("fpga_programmer executable");

		fpgaProgrammerLocation = new Text(shell, SWT.BORDER);
		fpgaProgrammerLocation.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

		locateButton(shell, fpgaProgrammerLocation);

		lblSerialPort = new Label(shell, SWT.NONE);
		lblSerialPort.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblSerialPort.setText("serial port");

		comPortBox = new Combo(shell, SWT.NONE);
		comPortBox.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		comPortBox.setItems(SerialPortList.getPortNames());
		if (comPortBox.getItemCount() > 0) {
			comPortBox.select(0);
		}

		final Button btnUpdateList = new Button(shell, SWT.NONE);
		btnUpdateList.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
		btnUpdateList.setText("update list");
		btnUpdateList.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				final String[] portNames = SerialPortList.getPortNames();
				comPortBox.setItems(portNames);
				if (portNames.length > 0) {
					comPortBox.select(0);
				}
			}
		});

		lblSynplify = new Label(shell, SWT.NONE);
		lblSynplify.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblSynplify.setText("synplify executable");

		synplifyLocation = new Text(shell, SWT.BORDER);
		synplifyLocation.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

		locateButton(shell, synplifyLocation);

		lblActelTclShell = new Label(shell, SWT.NONE);
		lblActelTclShell.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblActelTclShell.setText("Actel TCL Shell executable");

		actTCLShellLocation = new Text(shell, SWT.BORDER);
		actTCLShellLocation.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

		locateButton(shell, actTCLShellLocation);

		new Label(shell, SWT.NONE);
		new Label(shell, SWT.NONE);

		final Button btnSaveSettings = new Button(shell, SWT.NONE);
		btnSaveSettings.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
		btnSaveSettings.setText("Save settings");
		btnSaveSettings.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {
				saveToSettings();
				shell.close();
				helper.updateServices();
			}

		});
		loadFromSettings();
		shell.pack();
		final Point size = shell.getSize();
		if (size.x > 700) {
			shell.setSize(700, size.y);
		}
		return shell;
	}

	public void locateButton(final Shell shell, final Text label) {
		final Button btnLocate = new Button(shell, SWT.NONE);
		btnLocate.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
		btnLocate.setText("locate");
		btnLocate.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				final FileDialog fd = new FileDialog(shell, SWT.OPEN);
				fd.setFilterPath(label.getText());
				final String open = fd.open();
				if (open != null) {
					label.setText(open);
				}
			}
		});
	}

	protected void saveToSettings() {
		config.acttclsh = new File(actTCLShellLocation.getText());
		config.synplify = new File(synplifyLocation.getText());
		config.progammer = new File(fpgaProgrammerLocation.getText());
		config.comPort = comPortBox.getText();
		config.saveToPreferences(Preferences.userNodeForPackage(GuiClient.class));
	}

	public void validateSettings() {
		updateLabel(new File(synplifyLocation.getText()), lblSynplify);
		updateLabel(new File(actTCLShellLocation.getText()), lblActelTclShell);
		updateLabel(new File(fpgaProgrammerLocation.getText()), lblFpgaprogrammerExecutable);
		if (!Arrays.asList(SerialPortList.getPortNames()).contains(comPortBox.getText())) {
			lblSerialPort.setForeground(Display.getCurrent().getSystemColor(SWT.COLOR_RED));
		} else {
			lblSerialPort.setForeground(null);
		}
	}

	private void updateLabel(File file, Label label) {
		if (!file.exists() || !file.canExecute()) {
			if (!file.exists()) {
				label.setToolTipText("The file " + file.getAbsolutePath() + " can not be found");
			} else {
				label.setToolTipText("The file " + file.getAbsolutePath() + " is not executable");
			}
			label.setForeground(Display.getCurrent().getSystemColor(SWT.COLOR_RED));
		} else {
			label.setToolTipText(null);
			label.setForeground(null);
		}
	}

	private void loadFromSettings() {
		config.loadFromPref(Preferences.userNodeForPackage(GuiClient.class));
		actTCLShellLocation.setText(config.acttclsh.getAbsolutePath());
		synplifyLocation.setText(config.synplify.getAbsolutePath());
		fpgaProgrammerLocation.setText(config.progammer.getAbsolutePath());
		validateSettings();
	}

}
