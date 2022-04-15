/*
 * Copyright (C) 2022 Diego Andrés Gutiérrez Berón
 * 
 * Some parts of this software were made as adaptations of ideas other than mine.
 * In any case, due credit is given for any ideas or code that served as an
 * implementation base for this software.
 *
 * THIS PROGRAM IS LICENSED UNDER THE TERMS OF THE GNU GENERAL PUBLIC LICENSE
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package actions;

import emulator.Chirp8;
import dialogs.StatusDialog;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JFrame;

/**
 * @author Diego Gutierrez
 *
 * Definicion de "Debugger" action.
 */
public class DebuggerAction extends AbstractAction {

    JFrame ventana;

    public DebuggerAction(String name, ImageIcon icon, String shortDescription, Integer mnemonic) {
        super(name, icon);
        putValue(SHORT_DESCRIPTION, shortDescription);
        putValue(MNEMONIC_KEY, mnemonic);
    }

    public DebuggerAction(String name, String shortDescription, Integer mnemonic) {
        super(name);
        putValue(SHORT_DESCRIPTION, shortDescription);
        putValue(MNEMONIC_KEY, mnemonic);
    }

    public DebuggerAction(String name, String shortDescription) {
        super(name);
        putValue(SHORT_DESCRIPTION, shortDescription);
    }

    public void actionPerformed(ActionEvent e) {
        JDialog statusDialog = createDialog(ventana);
        statusDialog.setVisible(true);
    }

    public JDialog createDialog(JFrame frame) {

        Chirp8 mainWindow = (Chirp8) frame;
        StatusDialog statusDialog = mainWindow.getStatusDialog();

        if (statusDialog == null) {
            statusDialog = new StatusDialog(frame, false);
            mainWindow.setStatusDialog(statusDialog);
            statusDialog.setLocationRelativeTo(null);
        }

        statusDialog.setVisible(true);

        return statusDialog;
    }

    public JFrame getVentana() {
        return ventana;
    }

    public void setVentana(JFrame ventana) {
        this.ventana = ventana;
    }

}
