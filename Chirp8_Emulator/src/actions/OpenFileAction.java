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

import emulator.Chip8_CPU;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * @author Diego Gutierrez
 * 
 * Definicion de "Open File" action.
 */
public class OpenFileAction extends AbstractAction {

    private JFrame ventana;
    private Chip8_CPU cpu;

    public OpenFileAction(String name, ImageIcon icon, String shortDescription, Integer mnemonic) {
        super(name, icon);
        putValue(SHORT_DESCRIPTION, shortDescription);
        putValue(MNEMONIC_KEY, mnemonic);
    }

    public OpenFileAction(String name, String shortDescription, Integer mnemonic) {
        super(name);
        putValue(SHORT_DESCRIPTION, shortDescription);
        putValue(MNEMONIC_KEY, mnemonic);
    }

    public OpenFileAction(String name, String shortDescription) {
        super(name);
        putValue(SHORT_DESCRIPTION, shortDescription);
    }

    @Override
    public void actionPerformed(ActionEvent e) {

        JFileChooser fileChooser = createFileChooser();

        int returnVal = fileChooser.showOpenDialog(ventana);

        if (returnVal == JFileChooser.APPROVE_OPTION) {

            File file = fileChooser.getSelectedFile();

            if (file.isFile()) {
                try {
                    if (cpu.isAlive()) {
                        cpu.interrupt();
                        System.out.println("Se interrumpio el hilo de ejecucion de la CPU. Motivo: se esta abriendo un archivo de ROM para su ejecucion");
                    }

                    cpu.cargarPrograma(file.getAbsolutePath());

                    if (cpu.getState() == Thread.State.NEW) {
                        cpu.start();
                    }

                } catch (IOException ex) {
                    Logger.getLogger(OpenFileAction.class.getName()).log(Level.SEVERE, null, ex);
                }
            }

        } else {
            if (returnVal == JFileChooser.CANCEL_OPTION) {
                System.out.println("Se cancelo la seleccion");
            } else {
                System.out.println("Ha ocurrido un error al seleccionar el archivo a abrir");
            }
        }
    }

    public JFileChooser createFileChooser() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Abrir archivo");

        FileFilter filter1 = new FileNameExtensionFilter("Archivo de imagen ROM CHIP-8 (*.ch8, *.c8)", "ch8", "c8");
        FileFilter filter2 = new FileNameExtensionFilter("Archivo generico de imagen de ROM CHIP-8 (*.rom)", "rom");

        fileChooser.setCurrentDirectory(new File("."));
        fileChooser.setAcceptAllFileFilterUsed(false);
        fileChooser.setFileFilter(filter1);
        fileChooser.setFileFilter(filter2);

        return fileChooser;
    }

    public JFrame getVentana() {
        return ventana;
    }

    public void setVentana(JFrame ventana) {
        this.ventana = ventana;
    }

    public Chip8_CPU getCpu() {
        return cpu;
    }

    public void setCpu(Chip8_CPU cpu) {
        this.cpu = cpu;
    }

}
