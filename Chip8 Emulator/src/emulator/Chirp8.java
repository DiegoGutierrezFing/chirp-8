/*
 * Copyright (C) 2022 Diego Andrés Gutiérrez Berón
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
package emulator;

import dialogs.AboutDialog;
import dialogs.StatusDialog;
import actions.DebuggerAction;
import actions.AboutAction;
import actions.ConfigAction;
import actions.ExitAction;
import actions.OpenFileAction;
import dialogs.ConfigDialog;
import java.awt.*;
import java.awt.event.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;

public class Chirp8 extends JFrame {

    // Acciones de menu
    private AboutAction aboutAction;
    private OpenFileAction openFileAction;
    private ConfigAction configAction;
    private ExitAction exitAction;
    private DebuggerAction debuggerAction;

    private static JFrame ventana;
    private static JPanel video;
    private static StatusDialog statusDialog;
    private static AboutDialog aboutDialog;
    private static ConfigDialog configDialog;

    // Variables de dimensiones iniciales de ventana 
    static final int ANCHO = 640;
    static final int ALTO = ANCHO / 2;
    static final double relacionAspecto = ANCHO / ALTO;

    private static Chip8_CPU cpu;
    private static KeyboardChip8Event keyEvent;

    public Chirp8() {
        createActions();
        createMenuBar();
    }

    private void createActions() {

        // crear los actions
        aboutAction = new AboutAction("Acerca de ...", "Informacion acerca de la aplicacion");
        openFileAction = new OpenFileAction("Abrir imagen ROM", "Abrir un archivo de imagen de ROM desde el sistema de ficheros");
        configAction = new ConfigAction("Propiedades", "Configuracion del interprete");
        exitAction = new ExitAction("Salir", "Salir de la aplicacion");
        debuggerAction = new DebuggerAction("Debugger", "mostrar estado del emulador");

        openFileAction.setCpu(cpu);
        openFileAction.setVentana(ventana);
        aboutAction.setVentana(ventana);
        debuggerAction.setVentana(ventana);
    }

    /**
     * Crear un JMenuBar y asignar los actions a los elementos del menu.
     */
    private JMenuBar createMenuBar() {
        // crear la barra de menu
        JMenuBar menuBar = new JMenuBar();

        // crear los menus de la barra de menu
        JMenu fileMenu = new JMenu("Archivo");
        JMenu configMenu = new JMenu("Configuracion");
        JMenu helpMenu = new JMenu("Ayuda");

        // crear los items de cada menu, utilizando los actions creados anteriormente
        JMenuItem aboutMenuItem = new JMenuItem(aboutAction);
        JMenuItem openFileMenuItem = new JMenuItem(openFileAction);
        JMenuItem exitMenuItem = new JMenuItem(exitAction);
        JMenuItem configMenuItem = new JMenuItem(configAction);
        JMenuItem debuggerMenuItem = new JMenuItem(debuggerAction);

        // agregar los items de menu al menu al que corresponden
        helpMenu.add(aboutMenuItem);
        fileMenu.add(openFileMenuItem);
        configMenu.add(configMenuItem);
        fileMenu.add(exitMenuItem);
        configMenu.add(debuggerMenuItem);

        // agregar los menus y sus items a la barra de menu
        menuBar.add(fileMenu);
        menuBar.add(configMenu);
        menuBar.add(helpMenu);

        return menuBar;
    }

    private JFrame createAndShowGUI() {

        // Crear la ventana de la aplicacion
        ventana = new JFrame("CHIRP-8 : chip-8 emulator/interpreter by Diego Gutiérrez");

        // Establecer operacion por defecto al pulsar el boton "cerrar ventana"
        ventana.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Establecer procesamiento de eventos del teclado
        keyEvent = new KeyboardChip8Event(ventana, cpu);

        // Construir el panel con la imagen de pantalla del interprete
        video = new JPanel() {
            @Override
            public void paint(Graphics g) {
                super.paint(g);
                g.drawImage(cpu.pantalla, 0, 0, getWidth(), getHeight(), this);
            }
        };

        // Establecer tamaño preferido de panel de video
        video.setPreferredSize(new Dimension(ANCHO, ALTO));

        // Agregar el panel de video a la ventana de la aplicacion
        ventana.add(video);

        // Agregar barra de menu a la ventana de la aplicacion
        ventana.setJMenuBar(createMenuBar());

        // Establecer el tamaño de la ventana teniendo en cuenta el tamaño de cada componente.
        ventana.pack();

        // Establece la localizacion de la ventana sin origen de coordenadas (se abre la ventana en el centro de la pantalla)
        ventana.setLocationRelativeTo(null);

        // Mostrar la ventana
        ventana.setVisible(true);

        return ventana;

    }

    public static void main(String[] args) {

        // crear un objeto interprete
        cpu = new Chip8_CPU();

        // inicializarlo
        cpu.chip8Inicializar();

        // Crear la interfaz de usuario (GUI)
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                try {

                    /*
                     * Establecer el tema (look and feel) de la interfaz de
                     * usuario a la definida por defecto en el sistema operativo
                     * en el que se ejecute el programa
                     */
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

                    // Crear la interfaz de usuario y mostrarla en pantalla
                    new Chirp8().createAndShowGUI();

                } catch (ClassNotFoundException ex) {
                    Logger.getLogger(Chirp8.class.getName()).log(Level.SEVERE, null, ex);
                } catch (InstantiationException ex) {
                    Logger.getLogger(Chirp8.class.getName()).log(Level.SEVERE, null, ex);
                } catch (IllegalAccessException ex) {
                    Logger.getLogger(Chirp8.class.getName()).log(Level.SEVERE, null, ex);
                } catch (UnsupportedLookAndFeelException ex) {
                    Logger.getLogger(Chirp8.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        });

        /*
         * Crear un temporizador que refresque el panel de video cada 15 ms en
         * el hilo de despacho de eventos de Swing.
         * Referencia: https://www.cs.rutgers.edu/courses/111/classes/fall_2011_venugopal/texts/notes-java/other/10time/20timer.html
         */
        Timer mainGUITimer = new Timer(15, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                // Ampliacion o reduccion de tamaño de pantalla segun se cambie el tamaño de la ventana, manteniendo la relacion de aspecto
                ventana.setSize(ventana.getWidth(), (int) Math.round((double) ventana.getWidth() / relacionAspecto));

                if (cpu.isDrawFlag()) {
                    ventana.repaint();
                }
            }
        });

        /*
         * Crear un temporizador que refresque el dialogo de estado de emulacion
         * ("debugger") cada 15 milisegundos en el hilo de despacho de eventos
         * de Swing.
         * Referencia: https://www.cs.rutgers.edu/courses/111/classes/fall_2011_venugopal/texts/notes-java/other/10time/20timer.html
         */
        Timer statusDialogTimer = new Timer(15, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                if ((statusDialog != null)) {

                    if (statusDialog.isVisible()) {

                        // Actualizar valor del visor de opcode en ejecucion
                        statusDialog.setOpcodeValue(String.format("%04X", cpu.getOpcode()));

                        // Actualizar valor del visor del program counter
                        statusDialog.setProgramCounterValue(String.format("%04X", cpu.getProgramCounter()));

                        // Actualizar valor del visor del registro indice
                        statusDialog.setIndexRegisterValue(String.format("%04X", cpu.getRegistroIndice()));

                        // Actualizar valor del visor del temporizador de sonido
                        statusDialog.setSoundTimerValue(String.format("%02X", cpu.getSound_Timer()));

                        // Actualizar valor del visor del temporizador de delay (retardo)
                        statusDialog.setDelayTimerValue(String.format("%02X", cpu.getDelay_Timer()));

                        // Actualizar valor del visor de frecuencua de reloj de cpu
                        statusDialog.setClockFrequencyValue(Integer.toString(cpu.clockFrequency));

                        // Actualizar valores del visor de registros V
                        statusDialog.setV0Value(String.format("%04X", cpu.getRegistrosV()[0]));
                        statusDialog.setV1Value(String.format("%04X", cpu.getRegistrosV()[1]));
                        statusDialog.setV2Value(String.format("%04X", cpu.getRegistrosV()[2]));
                        statusDialog.setV3Value(String.format("%04X", cpu.getRegistrosV()[3]));
                        statusDialog.setV4Value(String.format("%04X", cpu.getRegistrosV()[4]));
                        statusDialog.setV5Value(String.format("%04X", cpu.getRegistrosV()[5]));
                        statusDialog.setV6Value(String.format("%04X", cpu.getRegistrosV()[6]));
                        statusDialog.setV7Value(String.format("%04X", cpu.getRegistrosV()[7]));
                        statusDialog.setV8Value(String.format("%04X", cpu.getRegistrosV()[8]));
                        statusDialog.setV9Value(String.format("%04X", cpu.getRegistrosV()[9]));
                        statusDialog.setVAValue(String.format("%04X", cpu.getRegistrosV()[10]));
                        statusDialog.setVBValue(String.format("%04X", cpu.getRegistrosV()[11]));
                        statusDialog.setVCValue(String.format("%04X", cpu.getRegistrosV()[12]));
                        statusDialog.setVDValue(String.format("%04X", cpu.getRegistrosV()[13]));
                        statusDialog.setVEValue(String.format("%04X", cpu.getRegistrosV()[14]));
                        statusDialog.setVFValue(String.format("%04X", cpu.getRegistrosV()[15]));

                        // Actualizar valores del visor de contenido de memoria
                        //statusDialog.setjTextArea1(cpu.volcadoMemoria());
                    }
                }
            }
        });

        // Ejecutar los temporizadores que actualizan la interfaz de usuario
        mainGUITimer.start();
        statusDialogTimer.start();
    }

    public static StatusDialog getStatusDialog() {
        return statusDialog;
    }

    public static void setStatusDialog(StatusDialog statusDialog) {
        Chirp8.statusDialog = statusDialog;
    }

    public static AboutDialog getAboutDialog() {
        return aboutDialog;
    }

    public static void setAboutDialog(AboutDialog aboutDialog) {
        Chirp8.aboutDialog = aboutDialog;
    }

    public static ConfigDialog getConfigDialog() {
        return configDialog;
    }

    public static void setConfigDialog(ConfigDialog configDialog) {
        Chirp8.configDialog = configDialog;
    }

    public static Chip8_CPU getCpu() {
        return cpu;
    }

    public static void setCpu(Chip8_CPU cpu) {
        Chirp8.cpu = cpu;
    }

}

/**
 * Clase auxiliar para controlar eventos por teclado Se utilizo implementacion
 * de Referencia:
 * https://docs.oracle.com/javase/tutorial/uiswing/examples/events/KeyEventDemoProject/src/events/KeyEventDemo.java
 * Adaptada por Diego Gutierrez - Marzo 2022
 */
class KeyboardChip8Event implements KeyListener, ActionListener {

    private JFrame windowFrame;
    private Chip8_CPU cpu;

    public KeyboardChip8Event(JFrame windowFrame, Chip8_CPU cpu) {
        this.windowFrame = windowFrame;
        this.cpu = cpu;
        this.windowFrame.addKeyListener(this);
    }

    public void inicializarComponente(JFrame frame, Chip8_CPU cpu) {
        this.windowFrame = frame;
        this.cpu = cpu;
        this.windowFrame.addKeyListener(this);
    }

    /**
     * Handle the key typed event from the text field.
     */
    @Override
    public void keyTyped(KeyEvent e) {
        eventoTeclado(e, "KEY TYPED: ");
    }

    /**
     * Handle the key pressed event from the text field.
     */
    @Override
    public void keyPressed(KeyEvent e) {
        eventoTeclado(e, "KEY PRESSED: ");
    }

    /**
     * Handle the key released event from the text field.
     */
    @Override
    public void keyReleased(KeyEvent e) {
        eventoTeclado(e, "KEY RELEASED: ");
    }

    /**
     * Handle the button click.
     */
    @Override
    public void actionPerformed(ActionEvent e) {

        //Return the focus to the typing area.
        windowFrame.requestFocusInWindow();
    }

    private void eventoTeclado(KeyEvent e, String keyStatus) {

        //You should only rely on the key char if the event
        //is a key typed event.
        int id = e.getID();
        String keyString;
        if (id == KeyEvent.KEY_TYPED) {
            char c = e.getKeyChar();
            keyString = "key character = '" + c + "'";
        } else {
            int keyCode = e.getKeyCode();
            keyString = "key code = " + keyCode + " (" + KeyEvent.getKeyText(keyCode) + ")";
        }

        int modifiersEx = e.getModifiersEx();
        String modString = "extended modifiers = " + modifiersEx;
        String tmpString = KeyEvent.getModifiersExText(modifiersEx);
        if (tmpString.length() > 0) {
            modString += " (" + tmpString + ")";
        } else {
            modString += " (no extended modifiers)";
        }

        String actionString = "action key? ";
        if (e.isActionKey()) {
            actionString += "YES";
        } else {
            actionString += "NO";
        }

        String locationString = "key location: ";
        int location = e.getKeyLocation();
        switch (location) {
            case KeyEvent.KEY_LOCATION_STANDARD:
                locationString += "standard";
                break;
            case KeyEvent.KEY_LOCATION_LEFT:
                locationString += "left";
                break;
            case KeyEvent.KEY_LOCATION_RIGHT:
                locationString += "right";
                break;
            case KeyEvent.KEY_LOCATION_NUMPAD:
                locationString += "numpad";
                break;
            default:
                // (location == KeyEvent.KEY_LOCATION_UNKNOWN)
                locationString += "unknown";
                break;
        }

        if (keyStatus.contains("KEY PRESSED")) {
            keyboardDown(e);
            cpu.setTeclaPresionada(true);
        } else if (keyStatus.contains("KEY RELEASED")) {
            keyboardUp(e);
            cpu.setTeclaPresionada(false);
        }
    }

    public void keyboardDown(KeyEvent keyEvent) {

        // esc : Cerrar la ventana al pulsar escape o mostrar dialogo de cerrar
        if (keyEvent.getKeyCode() == KeyEvent.VK_ESCAPE) {
            this.windowFrame.dispose();
            System.exit(0);
        }

        // PageDown : Disminuir la frecuencia de la cpu a la mitad
        if (keyEvent.getKeyCode() == KeyEvent.VK_PAGE_DOWN) {
            if (this.cpu.clockFrequency / 2 > 0) {
                this.cpu.clockFrequency = (this.cpu.clockFrequency / 2);
                System.out.println("clockFrequency : " + this.cpu.clockFrequency);
            }
        }

        // PageUp : Aumentar la frecuencia de la cpu al doble
        if (keyEvent.getKeyCode() == KeyEvent.VK_PAGE_UP) {
            if (this.cpu.clockFrequency * 2 > 0) {
                this.cpu.clockFrequency = (this.cpu.clockFrequency * 2);
                System.out.println("clockFrequency : " + this.cpu.clockFrequency);
            }

        }

        // Pause : cambiar entre el modo normal (reanudar) o el modo paso a paso (en pausa)
        if (keyEvent.getKeyCode() == KeyEvent.VK_PAUSE) {

            if (cpu.isSingleStep()) {
                System.out.println("se salio del modo single step");
                cpu.setSingleStep(false);
            } else {
                System.out.println("se ingreso al modo single step");
                cpu.setSingleStep(true);
            }

        }

        // Space : reanudar la emulacion un paso a la vez (en pausa)
        if (keyEvent.getKeyCode() == KeyEvent.VK_SPACE) {

            if (cpu.isSingleStep()) {
                System.out.println("se presiono la tecla single step");
                cpu.setSingleStepKey(true);
            } else {
                System.out.println("se presiono la tecla single step pero no se encuentra en modo single step");
            }

        }

        switch (keyEvent.getKeyChar()) {
            case '1':
                cpu.keyboard[0x1] = 1;
                break;
            case '2':
                cpu.keyboard[0x2] = 1;
                break;
            case '3':
                cpu.keyboard[0x3] = 1;
                break;
            case '4':
                cpu.keyboard[0xC] = 1;
                break;
            case 'q':
                cpu.keyboard[0x4] = 1;
                break;
            case 'w':
                cpu.keyboard[0x5] = 1;
                break;
            case 'e':
                cpu.keyboard[0x6] = 1;
                break;
            case 'r':
                cpu.keyboard[0xD] = 1;
                break;
            case 'a':
                cpu.keyboard[0x7] = 1;
                break;
            case 's':
                cpu.keyboard[0x8] = 1;
                break;
            case 'd':
                cpu.keyboard[0x9] = 1;
                break;
            case 'f':
                cpu.keyboard[0xE] = 1;
                break;
            case 'z':
                cpu.keyboard[0xA] = 1;
                break;
            case 'x':
                cpu.keyboard[0x0] = 1;
                break;
            case 'c':
                cpu.keyboard[0xB] = 1;
                break;
            case 'v':
                cpu.keyboard[0xF] = 1;
                break;
            default:
                break;
        }
    }

    public void keyboardUp(KeyEvent keyEvent) {

        // Space : detener la emulacion un paso a la vez (en pausa)
        if (keyEvent.getKeyCode() == KeyEvent.VK_SPACE) {

            if (cpu.isSingleStep()) {
                System.out.println("se libero la tecla single step");
                cpu.setSingleStepKey(false);
            } else {
                System.out.println("se libero la tecla single step pero no se encuentra en modo single step");
            }
        }

        switch (keyEvent.getKeyChar()) {
            case '1':
                cpu.keyboard[0x1] = 0;
                break;
            case '2':
                cpu.keyboard[0x2] = 0;
                break;
            case '3':
                cpu.keyboard[0x3] = 0;
                break;
            case '4':
                cpu.keyboard[0xC] = 0;
                break;
            case 'q':
                cpu.keyboard[0x4] = 0;
                break;
            case 'w':
                cpu.keyboard[0x5] = 0;
                break;
            case 'e':
                cpu.keyboard[0x6] = 0;
                break;
            case 'r':
                cpu.keyboard[0xD] = 0;
                break;
            case 'a':
                cpu.keyboard[0x7] = 0;
                break;
            case 's':
                cpu.keyboard[0x8] = 0;
                break;
            case 'd':
                cpu.keyboard[0x9] = 0;
                break;
            case 'f':
                cpu.keyboard[0xE] = 0;
                break;
            case 'z':
                cpu.keyboard[0xA] = 0;
                break;
            case 'x':
                cpu.keyboard[0x0] = 0;
                break;
            case 'c':
                cpu.keyboard[0xB] = 0;
                break;
            case 'v':
                cpu.keyboard[0xF] = 0;
                break;
            default:
                break;
        }
    }
}
