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
package emulator;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/**
 * @author Diego Gutierrez.
 *
 * Primera version (implementacion en lenguaje C): Enero, Marzo 2013. Segunda
 * version (implementacion en lenguaje Java): Febrero, Marzo 2022. Nota:
 * 08/03/2022 - Version comprobada con ROM de testeo: opcodes OK.
 */
public class Chip8_CPU extends Thread {

    /*
     * Mapa de memoria del sistema
     * 0x000-0x1FF - Interprete Chip 8 (contiene el conjunto de fuentes en el emulador).
     * 0x050-0x0A0 - Usado por el conjunto de fuentes integrado de 4x5 pixeles (0-F).
     * 0x200-0xFFF - ROM de Programa y area de memoria RAM de trabajo.
     *
     * Las direcciones de memoria del CHIP-8 tienen un rango 200h a FFFh, lo que hacen 3.584 bytes.
     * La razón del porqué la memoria comienza en 200h varía de acuerdo a la máquina.
     * Para el Cosmac VIP y el Telmac 1800, los primeros 512 bytes son reservados para
     * el intérprete. En esas máquinas, los 256 bytes más altos (F00h-FFFh en
     * máquinas de 4K) fueron reservados para el refresco de pantalla, y los 96
     * bytes más bajos (EA0h-EFFh) fueron reservados para los llamados de la
     * pila, uso interno y otras variables.
     *
     * Nota: en esta version, la pila se implementó como un arreglo por fuera de la memoria principal.
     * Normalmente, la pila forma parte de la memoria según lo indicado mas arriba.
     */
    
    /* Definiciones de componentes Hardware e Implementacion de la maquina CHIP-8 */
    private int opcode;                     // Codigo de instruccion opcode en uso actualmente.
    public int[] memoria = new int[4096];   // Memoria (RAM y ROM) disponible en la maquina CHIP-8 (4096 bytes = 4KiB).
    private int[] registrosV = new int[16]; // Registros de la CPU.
    private int registroIndice;             // Registro Indice: utilizado en operaciones de memoria. 0x000 a 0xFFF.
    private int programCounter;             // Contador de Programa (Program Counter, PC): 0x000 a 0xFFF.

    public int clockFrequency = 1760000;    // Frecuencia de la CPU en Hz (1.76 MHz en el COSMAC VIP)
    private int clockPulses = 0;

    /**
     * Sub-sistema de Video (Gráficos).
     *
     * La Resolución de Pantalla estándar es de 64×32 píxels, y la profundidad
     * del color es Monocromo (solo 2 colores, en general representado por los
     * colores blanco y negro). Los gráficos son dibujados en pantalla solo
     * mediante Sprites los cuales son de 8 pixels de ancho por 1 a 15 pixels de
     * alto. Si un pixel del Sprite está activo, entonces se pinta el color del
     * respectivo pixel en la pantalla, en cambio si no lo está, no se hace
     * nada. El flag de acarreo o carry flag (VF) se pone a 1 si cualquier pixel
     * de la pantalla se borra (se pasa de 1 a 0) mientras un pixel se está
     * pintando. Esto se utiliza para la deteccion de colisiones (es decir,
     * cuando un sprite "colisiona" con otro).
     *
     * The graphics system: The chip 8 has one instruction that draws sprite to
     * the screen. Drawing is done in XOR mode and if a pixel is turned off as a
     * result of drawing, the VF register is set. This is used for collision
     * detection.
     *
     * The graphics of the Chip 8 are black and white and the screen has a total
     * of 2048 pixels (64 x 32). This can easily be implemented using an array
     * that hold the pixel state (1 or 0).
     */
    private static final int WIDTH = 64;
    private static final int HEIGHT = 32;
    private int GFX[][] = new int[WIDTH][HEIGHT];   // area de video (pantalla) de 64x32 pixeles.

    public BufferedImage pantalla;
    public Chip8_Screen screen = new Chip8_Screen(WIDTH, HEIGHT);

    private boolean drawFlag;                       // bandera de estado de dibujado de pantalla: si es true, significa que debe redibujarse la pantalla.

    /**
     * Temporizadores (Timers)
     *
     * El CHIP-8 tiene 2 timers o temporizadores. Ambos corren hacia atrás hasta
     * llegar a 0 y lo hacen a 60 hertz.
     *
     * Timer para Retardo (Delay): este timer se usado para sincronizar los
     * eventos. Este valor puede ser escrito y leído.
     *
     * Timer para Sonido: Este timer es usado para efectos de sonidos. Cuando el
     * valor no es 0, se escucha un beep. Debe recordarse que el sonido a emitir
     * debe ser de un solo tono.
     */
    private int delay_Timer;    // Registro Temporizador de retardo: se utiliza para sincronizar eventos.
    private int sound_Timer;    // Registro Temporizador de sonido: se utiliza para efectos de sonidos.

    /**
     * La pila o stack
     *
     * La pila solo se usa para almacenar direcciones que serán usadas luego, al
     * regresar de una subrutina. La versión original 1802 permitía almacenar 48
     * bytes hacia arriba en 12 niveles de profundidad. Las implementaciones
     * modernas en general tienen al menos 16 niveles.
     */
    private int stack[] = new int[16];  // Pila (Stack): estructura para almacenar direcciones de memoria.
    private int stackPointer;           // Puntero de pila (Stack Pointer, SP): apunta a una direccion de memoria almacenada dentro del Stack.

    /**
     * Entrada
     *
     * La entrada está hecha con un teclado de tipo hexadecimal que tiene 16
     * teclas en un rango de 0 a F. Las teclas '8', '4', '6' y '2' son las
     * típicas usadas para las direcciones. Se usan 3 opcodes para detectar la
     * entrada. Una se activa si la tecla es presionada, el segundo hace lo
     * mismo cuando la no ha sido presionada y el tercero espera que se presione
     * una tecla. Estos 3 opcodes se almacenan en uno de los registros de datos.
     */
    public int keyboard[] = new int[16];

    private boolean teclaPresionada;    // Bandera de tecla presionada

    private Random rand;                // Generador de numeros pseudoaleatorios

    private boolean singleStep = false;    // Variable de control de modo paso a paso (single step)
    private boolean singleStepKey = false;    // Bandera de tecla de paso a paso presionada

    /* fuentes del sistema */
    private int[] chip8_fontset
            = {
                0xF0, 0x90, 0x90, 0x90, 0xF0, //0
                0x20, 0x60, 0x20, 0x20, 0x70, //1
                0xF0, 0x10, 0xF0, 0x80, 0xF0, //2
                0xF0, 0x10, 0xF0, 0x10, 0xF0, //3
                0x90, 0x90, 0xF0, 0x10, 0x10, //4
                0xF0, 0x80, 0xF0, 0x10, 0xF0, //5
                0xF0, 0x80, 0xF0, 0x90, 0xF0, //6
                0xF0, 0x10, 0x20, 0x40, 0x40, //7
                0xF0, 0x90, 0xF0, 0x90, 0xF0, //8
                0xF0, 0x90, 0xF0, 0x10, 0xF0, //9
                0xF0, 0x90, 0xF0, 0x90, 0x90, //A
                0xE0, 0x90, 0xE0, 0x90, 0xE0, //B
                0xF0, 0x80, 0x80, 0x80, 0xF0, //C
                0xE0, 0x90, 0x90, 0x90, 0xE0, //D
                0xF0, 0x80, 0xF0, 0x80, 0xF0, //E
                0xF0, 0x80, 0xF0, 0x80, 0x80 //F
            };

    /*
     * Tabla de instrucciones
     *
     * CHIP-8 tiene 35 instrucciones, las cuales tienen un tamaño de 2 bytes.
     * Estos opcodes se listan a continuación, en hexadecimal y con los
     * siguientes símbolos:
     *
     * NNN: Dirección KK: constante de 8-bit N: constante de 4-bit X e Y:
     * registro de 4-bit
     *
     * PC: Contador de programa (del inglés Program Counter) SP: Puntero de pila
     * (del inglés Stack Pointer)
     *
     *
     * Opcode Explicación
     *
     * 0NNN Salta a un código de rutina en NNN. Se usaba en los viejos
     * computadores que implementaban Chip-8. Los actuales intérpretes lo
     * ignoran.
     *
     * 00E0 Limpia la pantalla.
     *
     * 00EE Retorna de una subrutina. Se decrementa en 1 el Stack Pointer (SP).
     * El intérprete establece el Program Counter como la dirección donde apunta
     * el SP en la Pila.
     *
     * 1NNN Salta a la dirección NNN. El intérprete establece el Program Counter
     * a NNN.
     *
     * 2NNN Llama a la subrutina NNN. El intérprete incrementa el Stack Pointer,
     * luego pone el actual PC en el tope de la Pila. El PC se establece a NNN.
     *
     * 3XKK Salta a la siguiente instrucción si VX = NN. El intérprete compara
     * el registro VX con el KK, y si son iguales, incrementa el PC en 2.
     *
     * 4XKK Salta a la siguiente instrucción si VX != KK. El intérprete compara
     * el registro VX con el KK, y si no son iguales, incrementa el PC en 2.
     *
     * 5XY0 Salta a la siguiente instrucción si VX = VY. El intérprete compara
     * el registro VX con el VY, y si no son iguales, incrementa el PC en 2.
     *
     * 6XKK Hace VX = KK. El intérprete coloca el valor KK dentro del registro
     * VX.
     *
     * 7XKK Hace VX = VX + KK. Suma el valor de KK al valor de VX y el resultado
     * lo deja en VX.
     *
     * 8XY0 Hace VX = VY. Almacena el valor del registro VY en el registro VX.
     *
     * 8XY1 Hace VX = VX OR VY. Realiza un bitwise OR (OR Binario) sobre los
     * valores de VX y VY, entonces almacena el resultado en VX. Un bitwise OR
     * compara cada uno de los bit respectivos desde 2 valores, y si al menos
     * uno es true (1), entonces el mismo bit en el resultado es 1. De otra
     * forma es 0.
     *
     * 8XY2 Hace VX = VX AND VY. 8XY3 Hace VX = VX XOR VY.
     *
     * 8XY4 Suma VY a VX. VF se pone a 1 cuando hay un acarreo (carry), y a 0
     * cuando no.
     *
     * 8XY5 VY se resta de VX. VF se pone a 0 cuando hay que restarle un dígito
     * al numero de la izquierda, más conocido como "pedir prestado" o borrow, y
     * se pone a 1 cuando no es necesario.
     *
     * 8XY6 Setea VF = 1 o 0 según bit menos significativo de VX. Divide VX por
     * 2.
     *
     * 8XY7 Si VY > VX => VF = 1, sino 0. VX = VY - VX.
     *
     * 8XYE Setea VF = 1 o 0 según bit más significativo de VX. Multiplica VX
     * por 2.
     *
     * 9XY0 Salta a la siguiente instrucción si VX != VY.
     *
     * ANNN Setea I = NNNN.
     *
     * BNNN Salta a la ubicación V[0]+ NNNN.
     *
     * CXKK Setea VX = un Byte Aleatorio AND KK.
     *
     * DXYN Pinta un sprite en la pantalla. El interprete lee N bytes desde la
     * memoria, comenzando desde el contenido del registro I. Y se muestra dicho
     * byte en las posiciones VX, VY de la pantalla. A los sprites que se pintan
     * se le aplica XOR con lo que está en pantalla. Si esto causa que algún
     * pixel se borre, el registro VF se setea a 1, de otra forma se setea a 0.
     * Si el sprite se posiciona afuera de las coordenadas de la pantalla, dicho
     * sprite se le hace aparecer en el lado opuesto de la pantalla.
     *
     * EX9E Salta a la sgte. instrucción si valor de VX coincide con tecla
     * presionada.
     *
     * EXA1 Salta a la sgte. instrucción si valor de VX no coincide con tecla
     * presionada (soltar tecla). FX07 Setea Vx = valor del delay timer.
     *
     * FX0A Espera por una tecla presionada y la almacena en el registro.
     *
     * FX15 Setea Delay Timer = VX.
     *
     * FX18 Setea Sound Timer = VX.
     *
     * FX1E Indice = Indice + VX.
     *
     * FX29 Setea I = VX * largo Sprite Chip-8.
     *
     * FX33 Setea I = VX * largo Sprite Sprite Super Chip-8.
     *
     * FX55 Almacena centenas, decenas y unidades en la memoria[I], memoria[I+1]
     * y memoria[I+2].
     *
     * FX65 Guarda en memoria[I] valor de V0 a VX.
     */
    
    /*
     * Implementacion de emulacion de funciones del Hardware. *
     * Funciones auxiliares. *
     */
    
    /* borrar la pantalla */
    void limpiarPantalla() {
        for (int i = 0; i < WIDTH; i++) {
            for (int j = 0; j < HEIGHT; j++) {
                GFX[i][j] = 0x00;
            }
        }
    }

    /* funcion para realizar un volcado de memoria (memory dump) */
    String volcadoMemoria() {

        String volcado = "";

        for (int j = 1; j <= 4096; j++) {
            volcado += (String.format("%02X", ((memoria[j - 1])) /*& 0xFF*/) + " ");
            if ((j % 16) == 0) {
                volcado += "\n";
            }
        }
        return volcado;
    }

    /* funcion para detectar si se presiono una tecla */
    void detectarTecla() {

        for (int i = 0; i < 16; ++i) {
            if (keyboard[i] != 0) {
                registrosV[(opcode & 0x0F00) >> 8] = i;
                teclaPresionada = true;
            }
        }
    }

    /* funcion para inicializar el teclado */
    void inicializarTeclado() {
        for (int i = 0; i < 16; ++i) {
            keyboard[i] = 0x00;   //inicializar (limpiar) teclado
        }
    }

    /* funcion para inicializar el interprete */
    void chip8Inicializar() {

        // Inicializar los registros y la memoria una vez
        //inicializar la memoria
        for (int i = 0; i < 4096; ++i) {
            memoria[i] = 0x0000;
        }

        //inicializar los registros de la CPU
        for (int i = 0; i < 16; ++i) {
            registrosV[i] = 0x0000;
        }

        //inicializar la pila (stack)
        for (int i = 0; i < 16; ++i) {
            stack[i] = 0x0000;
        }

        //inicializar teclado
        inicializarTeclado();

        //cargar fuentes del sistema
        for (int i = 0; i < 80; ++i) {
            memoria[i] = chip8_fontset[i];
        }

        programCounter = 0x0200;    // inicializar el Contador de Programa: el codigo del programa inicia en la direccion 0x200
        stackPointer = 0x0000;      // inicializar el Puntero de Pila
        registroIndice = 0x0000;    // inicializar el Registro Indice
        opcode = 0x0000;            // inicializar el registro de codigo de instruccion en uso actualmente

        limpiarPantalla();          // inicializar la pantalla
        drawFlag = false;            // inicializar bandera de estado de dibujado de pantalla

        // inicializar los temporizadores
        delay_Timer = 0;
        sound_Timer = 0;

        //inicializar generador de numeros pseudoaleatorios
        rand = new Random();

        clockPulses = 0;
    }

    /* funcion para emular un ciclo de ejecucion de instruccion */
    void chip8EmularCiclo() throws LineUnavailableException, InterruptedException {

        // Variable para medicion de tiempos
        // Permite calcular el avance del reloj del sistema (cpu ticks value).
        long t1 = System.nanoTime();

        // Obtener Opcode (Fetch Opcode)
        opcode = fetch(programCounter);

        // Decodificar y ejecutar Opcode obtenido desde memoria
        decodeAndExecute(opcode);

        // Variable para medicion de tiempos
        // Permite calcular el avance del reloj del sistema (cpu ticks value) luego de obtener, decodificar y ejecutar una instruccion.
        long t2 = System.nanoTime();

        // Emulacion simple de pulso de reloj del CPU (1.76 MHz = 568.1818 nanosegundos)
        TimeUnit.NANOSECONDS.sleep(1000000000 / clockFrequency);

        // Contador de pulsos de reloj de cpu (implementacion simple con enteros, podria hacerse mas exacto si se implementara con flotantes)
        // Se utiliza para poder actualizar los temporizadores con una frecuencia de 60 Hz segun se indica en la documentacion de chip-8
        clockPulses += 1 + (t2 - t1) / ((double) 1000000000 / clockFrequency);

        // Actualizar temporizadores
        if ((clockFrequency / 60) - clockPulses >= 0) {

            if ((delay_Timer) > 0) {

                delay_Timer--;

                // Hacer que el contador siempre este en el rango [0,255] y vuelva a 0 si sobrepasa el valor 255
                delay_Timer &= 0xFF;
            }

            if ((sound_Timer) > 0) {
                if ((sound_Timer) == 1) {
                    // Generar un tono de 1000 Hz y 50 ms de duracion.
                    Sound.tone(1000, 50);
                }

                sound_Timer--;

                // Hacer que el contador siempre este en el rango [0,255] y vuelva a 0 si sobrepasa el valor 255
                sound_Timer &= 0xFF;
            }

            clockPulses = 0;
        }
    }

    /* funcion para emular un ciclo de ejecucion de instruccion en modo paso a paso (single step) */
    void chip8EmularCicloSingleStep() throws LineUnavailableException, InterruptedException {

        // Variable para medicion de tiempos
        // Permite calcular el avance del reloj del sistema (cpu ticks value).
        long t1 = System.nanoTime();

        // Obtener Opcode (Fetch Opcode)
        opcode = fetch(programCounter);

        // Decodificar y ejecutar Opcode obtenido desde memoria
        decodeAndExecute(opcode);

        // Variable para medicion de tiempos
        // Permite calcular el avance del reloj del sistema (cpu ticks value) luego de obtener, decodificar y ejecutar una instruccion.
        long t2 = System.nanoTime();
        
        // Emulacion simple de pulso de reloj del CPU (1.76 MHz = 568.1818 nanosegundos)
        TimeUnit.NANOSECONDS.sleep(1000000000 / clockFrequency);

        // Contador de pulsos de reloj de cpu (implementacion simple con enteros, podria hacerse mas exacto si se implementara con flotantes)
        // Se utiliza para poder actualizar los temporizadores con una frecuencia de 60 Hz segun se indica en la documentacion de chip-8
        clockPulses += 1 + (t2 - t1) / ((double) 1000000000 / clockFrequency);

        // Actualizar temporizadores
        if ((clockFrequency / 60) - clockPulses >= 0) {

            if ((delay_Timer) > 0) {

                delay_Timer--;

                // Hacer que el contador siempre este en el rango [0,255] y vuelva a 0 si sobrepasa el valor 255
                delay_Timer &= 0xFF;
            }

            if ((sound_Timer) > 0) {
                if ((sound_Timer) == 1) {
                    // Generar un tono de 1000 Hz y 50 ms de duracion.
                    Sound.tone(1000, 50);
                }

                sound_Timer--;

                // Hacer que el contador siempre este en el rango [0,255] y vuelva a 0 si sobrepasa el valor 255
                sound_Timer &= 0xFF;
            }

            clockPulses = 0;

        }
    }

    public void cargarPrograma(String filename) throws IOException {
        chip8Inicializar();

        // Si se ejecuta el programa desde una terminal de linea de comando, imprimir un mensaje indicando que se esta abriendo un archivo
        Logger.getLogger(Chip8_CPU.class.getName()).log(Level.INFO, "Abriendo archivo: " + filename);

        byte[] fileArray;

        // Abrir archivo
        Path file = Paths.get(filename);
        fileArray = Files.readAllBytes(file);

        // Verificar tamaño de archivo imprimiendo en linea de comandos el valor que se obtiene
        long lSize = fileArray.length;
        Logger.getLogger(Chip8_CPU.class.getName()).log(Level.INFO, "Tamaño del archivo en bytes: " + lSize);

        // Copiar bytes del archivo a la memoria del Chip8
        if ((4096 - 512) > lSize) {
            int i;
            for (i = 0; i < lSize; ++i) {
                memoria[i + 512] = fileArray[i] & 0xFF;
                memoria[i + 512] &= 0xFF;
            }
        } else {
            Logger.getLogger(Chip8_CPU.class.getName()).log(Level.SEVERE, ("Error: ROM demasiado grande para la memoria chip-8 disponible"));
        }

    }

    public int[] getChip8_fontset() {
        return chip8_fontset;
    }

    public void setChip8_fontset(int[] chip8_fontset) {
        this.chip8_fontset = chip8_fontset;
    }

    public int getOpcode() {
        return opcode;
    }

    public void setOpcode(int opcode) {
        this.opcode = opcode;
    }

    public int[] getMemoria() {
        return memoria;
    }

    public void setMemoria(int[] memoria) {
        this.memoria = memoria;
    }

    public int[] getRegistrosV() {
        return registrosV;
    }

    public void setRegistrosV(int[] registrosV) {
        this.registrosV = registrosV;
    }

    public int getRegistroIndice() {
        return registroIndice;
    }

    public void setRegistroIndice(int registroIndice) {
        this.registroIndice = registroIndice;
    }

    public int getProgramCounter() {
        return programCounter;
    }

    public void setProgramCounter(int programCounter) {
        this.programCounter = programCounter;
    }

    public int[][] getGFX() {
        return GFX;
    }

    public void setGFX(int[][] GFX) {
        this.GFX = GFX;
    }

    public boolean isDrawFlag() {
        return drawFlag;
    }

    public void setDrawFlag(boolean drawFlag) {
        this.drawFlag = drawFlag;
    }

    public int getDelay_Timer() {
        return delay_Timer;
    }

    public void setDelay_Timer(int delay_Timer) {
        this.delay_Timer = delay_Timer;
    }

    public int getSound_Timer() {
        return sound_Timer;
    }

    public void setSound_Timer(int sound_Timer) {
        this.sound_Timer = sound_Timer;
    }

    public int[] getStack() {
        return stack;
    }

    public void setStack(int[] stack) {
        this.stack = stack;
    }

    public int getStackPointer() {
        return stackPointer;
    }

    public void setStackPointer(int stackPointer) {
        this.stackPointer = stackPointer;
    }

    public int[] getKeyboard() {
        return keyboard;
    }

    public void setKeyboard(int[] keyboard) {
        this.keyboard = keyboard;
    }

    public boolean isTeclaPresionada() {
        return teclaPresionada;
    }

    public void setTeclaPresionada(boolean teclaPresionada) {
        this.teclaPresionada = teclaPresionada;
    }

    public Random getRand() {
        return rand;
    }

    public void setRand(Random rand) {
        this.rand = rand;
    }

    public boolean isSingleStep() {
        return singleStep;
    }

    public void setSingleStep(boolean singleStep) {
        this.singleStep = singleStep;
    }

    public boolean isSingleStepKey() {
        return singleStepKey;
    }

    public void setSingleStepKey(boolean singleStepKey) {
        this.singleStepKey = singleStepKey;
    }

    /**
     * Implementacion de fetch, decode y execute
     */
    public int fetch(int PC) {
        return ((memoria[PC] << 8)) | (memoria[PC + 1]);
    }

    public void decodeAndExecute(int opcode) {

        // Decodificar y ejecutar Opcode (Decode and execute Opcode)
        switch (opcode & 0xF000) {
            case 0x0000:
                switch (opcode & 0x00FF) {
                    case 0x00E0:
                        // 00E0: Limpia la pantalla
                        Ox00E0();
                        break;

                    case 0x00EE:
                        // 00EE: Retorna de una subrutina.
                        Ox00EE();
                        break;
                    default:
                        System.out.println("Opcode desconocido 0x00XX: " + Integer.toHexString(opcode));
                        break;
                }
                break;

            case 0x1000:
                // 1NNN: Salta a la dirección NNN.
                //El intérprete establece el Program Counter a NNN.
                Ox1NNN();
                break;

            case 0x2000:
                // 2NNN; Llama a la subrutina NNN.
                //El intérprete incrementa el Stack Pointer, luego de poner el actual PC en el tope de la Pila.
                //El PC se establece a NNN.
                Ox2NNN();
                break;

            case 0x3000:
                // 3XNN: Se saltea la siguiente instrucción si VX = KK.
                //El intérprete compara el registro VX con el KK, y si son iguales, incrementa el PC en 4.
                Ox3XNN();
                break;

            case 0x4000:
                // 4XNN: Se saltea la siguiente instrucción si VX != KK.
                //El intérprete compara el registro VX con el KK, y si son iguales, incrementa el PC en 4.
                Ox4XNN();
                break;

            case 0x5000:
                // 5XY0: Se saltea la siguiente instrucción si VX = VY.
                //El intérprete compara el registro VX con el VY, y si son iguales, incrementa el PC en 4.
                Ox5XY0();
                break;

            case 0x6000:
                //Hace VX = KK. El intérprete coloca el valor KK dentro del registro VX.
                Ox6XNN();
                break;

            case 0x7000:
                //Hace VX = VX + KK. Suma el valor de KK al valor de VX y el resultado lo deja en VX.
                Ox7XNN();
                break;

            case 0x8000:
                switch (opcode & 0x000F) {
                    case 0x0000:
                        //Hace VX = VY. Almacena el valor del registro VY en el registro VX.
                        Ox8XY0();
                        break;

                    case 0x0001:
                        //Hace VX = VX OR VY.
                        //Realiza un bitwise OR (OR Binario) sobre los valores de VX y VY, entonces almacena el resultado en VX.
                        //Un bitwise OR compara cada uno de los bit respectivos desde 2 valores, y si al menos uno es true (1),
                        //entonces el mismo bit en el resultado es 1. De otra forma es 0.
                        Ox8XY1();
                        break;

                    case 0x0002:
                        //Hace VX = VX AND VY.
                        Ox8XY2();
                        break;

                    case 0x0003:
                        //Hace VX = VX XOR VY.
                        Ox8XY3();
                        break;

                    case 0x0004:
                        //Suma VY a VX.
                        //VF se pone a 1 cuando hay un acarreo (carry), y a 0 cuando no.
                        Ox8XY4();
                        break;

                    case 0x0005:
                        //VY se resta de VX.
                        //VF se pone a 0 cuando hay que restarle un dígito al numero de la izquierda, más conocido como
                        //"pedir prestado" o borrow, y se pone a 1 cuando no es necesario.
                        Ox8XY5();
                        break;

                    case 0x0006:
                        //Setea VF = 1 o 0 según bit menos significativo de VX. Divide VX por 2.
                        Ox8XY6();
                        break;

                    case 0x0007:
                        //VX = VY - VX
                        //Si VY >= VX => VF = 1, sino 0. VX = VY - VX.
                        //(Nota: Revisar el signo de igualdad: deberia ser la misma condicion que la instruccion 8XY5)
                        Ox8XY7();
                        break;

                    case 0x000E:
                        //Establece VF = 1 o 0 según bit más significativo de VX. Multiplica VX por 2.
                        Ox8XYE();
                        break;
                    default:
                        System.out.println("Opcode desconocido 08XXX: " + Integer.toHexString(opcode));
                }
                break;

            case 0x9000:
                // 9XY0: Se saltea la siguiente instrucción si VX != VY.
                Ox9XY0();
                break;

            case 0xA000:
                // ANNN: Establece registroIndice = NNN.
                OxANNN();
                break;

            case 0xB000:
                // BNNN: Salta a la ubicación V0 + NNN.
                OxBNNN();
                break;

            case 0xC000:
                //Setea VX = un Byte Aleatorio AND NN.
                OxCXNN();
                break;

            case 0xD000:
                // DXYN: Draw a sprite at position VX, VY with N bytes of sprite data starting at the address stored in I
                // Set VF to 01 if any set pixels are changed to unset, and 00 otherwise
                OxDXYN();
                break;

            case 0xE000:

                switch (opcode & 0x00FF) {
                    case 0x009E:
                        // EX9E: Skips the next instruction if the key stored in VX is pressed.
                        OxEX9E();
                        break;

                    case 0x00A1:
                        // EXA1: Skips the next instruction if the key stored in VX is not pressed.
                        OxEXA1();
                        break;

                    default:
                        System.out.println("Opcode desconocido 0xEXXX: " + Integer.toHexString(opcode));
                }
                break;

            case 0xF000:
                switch (opcode & 0x00FF) {
                    case 0x0007:
                        // FX07: Setea Vx = valor del delay timer.
                        OxFX07();
                        break;

                    case 0x000A:
                        // FX0A: Espera por una tecla presionada y la almacena en el registro.
                        // Implementacion basada en la de Laurence Muller.
                        OxFX0A();
                        break;

                    case 0x0015:
                        // FX15: Establecer el delay timer a VX
                        OxFX15();
                        break;

                    case 0x0018:
                        // FX18: Establecer el sound timer a VX
                        OxFX18();
                        break;

                    case 0x001E:
                        // FX1E: Suma VX a I
                        // VF se establece a 1 cuando existe overflow de rango (registroIndice + VX > 0xFFF), y 0 cuando no se produce.
                        OxFX1E();
                        break;

                    case 0x0029:
                        // FX29: Set I to the memory address of the sprite data corresponding to the hexadecimal digit stored in register VX
                        OxFX29();
                        break;

                    case 0x0033:
                        // FX33: Stores the Binary-coded decimal representation of VX at the addresses I, I plus 1, and I plus 2
                        OxFX33();
                        break;

                    case 0x0055:
                        // FX55: Stores V0 to VX in memory starting at address I
                        OxFX55();
                        break;

                    case 0x0065:
                        // FX65: Fills V0 to VX with values from memory starting at address I
                        OxFX65();
                        break;

                    default:
                        System.out.println("Opcode desconocido 0xFXXX: " + Integer.toHexString(opcode));
                }
                break;

            default:
                System.out.println("Opcode desconocido: " + Integer.toHexString(opcode));
        }
    }

    /**
     * IMPLEMENTACION DE OPCODES
     */
    private void Ox00E0() {
        //Limpia la pantalla
        limpiarPantalla();
        drawFlag = true;
        programCounter += 2;
        //System.out.println("Opcode: " + getOpcodeAsString(opcode) + " " + getCPU_StatusAsString());
    }

    private void Ox00EE() {
        //Retorna de una subrutina.
        //Se decrementa en 1 el Stack Pointer (SP).
        //El intérprete establece el Program Counter como la dirección donde apunta el SP en la Pila.
        stackPointer--;
        programCounter = stack[stackPointer];
        programCounter += 2;
        //System.out.println("Opcode: " + getOpcodeAsString(opcode) + " " + getCPU_StatusAsString());
    }

    private void Ox1NNN() {
        //Salta a la dirección NNN.
        //El intérprete establece el Program Counter a NNN.
        programCounter = opcode & 0x0FFF;
        //System.out.println("Opcode: " + getOpcodeAsString(opcode) + " " + getCPU_StatusAsString());
    }

    private void Ox2NNN() {
        //Llama a la subrutina NNN.
        //El intérprete incrementa el Stack Pointer, luego de poner el actual PC en el tope de la Pila.
        //El PC se establece a NNN.

        stack[stackPointer] = programCounter;
        stackPointer++;
        programCounter = opcode & 0x0FFF;
        //System.out.println("Opcode: " + getOpcodeAsString(opcode) + " " + getCPU_StatusAsString());
    }

    private void Ox3XNN() {
        //Se saltea la siguiente instrucción si VX = KK.
        //El intérprete compara el registro VX con el KK, y si son iguales, incrementa el PC en 4.

        registrosV[(opcode & 0x0F00) >> 8] &= 0xFF;

        if (registrosV[(opcode & 0x0F00) >> 8] == (opcode & 0x00FF)) {
            programCounter += 4;
        } else {
            programCounter += 2;
        }
        //System.out.println("Opcode: " + getOpcodeAsString(opcode) + " " + getCPU_StatusAsString());
    }

    private void Ox4XNN() {
        //Se saltea la siguiente instrucción si VX != KK.
        //El intérprete compara el registro VX con el KK, y si son iguales, incrementa el PC en 4.

        registrosV[(opcode & 0x0F00) >> 8] &= 0xFF;

        if (registrosV[(opcode & 0x0F00) >> 8] != (opcode & 0x00FF)) {
            programCounter += 4;
        } else {
            programCounter += 2;
        }
        //System.out.println("Opcode: " + getOpcodeAsString(opcode) + " " + getCPU_StatusAsString());
    }

    private void Ox5XY0() {
        //Se saltea la siguiente instrucción si VX = VY.
        //El intérprete compara el registro VX con el VY, y si son iguales, incrementa el PC en 4.

        registrosV[(opcode & 0x00F0) >> 4] &= 0xFF;
        registrosV[(opcode & 0x0F00) >> 8] &= 0xFF;

        if (registrosV[(opcode & 0x0F00) >> 8] == registrosV[(opcode & 0x00F0) >> 4]) {
            programCounter += 4;
        } else {
            programCounter += 2;
        }
        //System.out.println("Opcode: " + getOpcodeAsString(opcode) + " " + getCPU_StatusAsString());
    }

    private void Ox6XNN() {
        //Hace VX = KK. El intérprete coloca el valor KK dentro del registro VX.

        registrosV[(opcode & 0x0F00) >> 8] = ((opcode & 0x00FF) & 0xFF);
        programCounter += 2;
        //System.out.println("Opcode: " + getOpcodeAsString(opcode) + " " + getCPU_StatusAsString());
    }

    private void Ox7XNN() {
        //Hace VX = VX + KK. Suma el valor de KK al valor de VX y el resultado lo deja en VX.

        registrosV[(opcode & 0x0F00) >> 8] &= 0xFF;

        if ((registrosV[(opcode & 0x0F00) >> 8] + (opcode & 0x00FF)) <= 255) {
            registrosV[(opcode & 0x0F00) >> 8] += (opcode & 0x00FF);
        } else {
            registrosV[(opcode & 0x0F00) >> 8] = registrosV[(opcode & 0x0F00) >> 8] + (opcode & 0x00FF) - 256;
        }
        programCounter += 2;
        //System.out.println("Opcode: " + getOpcodeAsString(opcode) + " " + getCPU_StatusAsString());
    }

    private void Ox8XY0() {
        //Hace VX = VY. Almacena el valor del registro VY en el registro VX.

        registrosV[(opcode & 0x0F00) >> 8] = registrosV[(opcode & 0x00F0) >> 4];
        programCounter += 2;
        //System.out.println("Opcode: " + getOpcodeAsString(opcode) + " " + getCPU_StatusAsString());
    }

    private void Ox8XY1() {
        //Hace VX = VX OR VY.
        //Realiza un bitwise OR (OR Binario) sobre los valores de VX y VY, entonces almacena el resultado en VX.
        //Un bitwise OR compara cada uno de los bit respectivos desde 2 valores, y si al menos uno es true (1),
        //entonces el mismo bit en el resultado es 1. De otra forma es 0.

        registrosV[(opcode & 0x0F00) >> 8] |= (registrosV[(opcode & 0x00F0) >> 4]);
        programCounter += 2;
        //System.out.println("Opcode: " + getOpcodeAsString(opcode) + " " + getCPU_StatusAsString());
    }

    private void Ox8XY2() {
        //Hace VX = VX AND VY.
        //Realiza un bitwise AND (AND Binario) sobre los valores de VX y VY, entonces almacena el resultado en VX.

        registrosV[(opcode & 0x0F00) >> 8] &= (registrosV[(opcode & 0x00F0) >> 4]);
        programCounter += 2;
        //System.out.println("Opcode: " + getOpcodeAsString(opcode) + " " + getCPU_StatusAsString());
    }

    private void Ox8XY3() {
        //Hace VX = VX XOR VY.

        registrosV[(opcode & 0x0F00) >> 8] ^= (registrosV[(opcode & 0x00F0) >> 4]);
        programCounter += 2;
        //System.out.println("Opcode: " + getOpcodeAsString(opcode) + " " + getCPU_StatusAsString());
    }

    private void Ox8XY4() {
        //Suma VY a VX.
        //VF se pone a 1 cuando hay un acarreo (carry), y a 0 cuando no.

        registrosV[(opcode & 0x00F0) >> 4] &= 0xFF;
        registrosV[(opcode & 0x0F00) >> 8] &= 0xFF;

        if ((registrosV[(opcode & 0x00F0) >> 4] + registrosV[(opcode & 0x0F00) >> 8]) > 255) {
            registrosV[0xF] = 1;
            registrosV[(opcode & 0x0F00) >> 8] = (registrosV[(opcode & 0x0F00) >> 8] + registrosV[(opcode & 0x00F0) >> 4]) - 256;

        } else {
            registrosV[0xF] = 0;
            registrosV[(opcode & 0x0F00) >> 8] = (registrosV[(opcode & 0x0F00) >> 8] + registrosV[(opcode & 0x00F0) >> 4]);
        }

        programCounter += 2;
        //System.out.println("Opcode: " + getOpcodeAsString(opcode) + " " + getCPU_StatusAsString());
    }

    private void Ox8XY5() {
        //8XY5
        //VY se resta de VX.
        //VF se pone a 0 cuando hay que restarle un dígito al numero de la izquierda, más conocido como
        //"pedir prestado" o borrow, y se pone a 1 cuando no es necesario.

        registrosV[(opcode & 0x00F0) >> 4] &= 0xFF;
        registrosV[(opcode & 0x0F00) >> 8] &= 0xFF;

        if (registrosV[(opcode & 0x00F0) >> 4] < (registrosV[(opcode & 0x0F00) >> 8])) {
            registrosV[0xF] = 1;
            registrosV[(opcode & 0x0F00) >> 8] = (registrosV[(opcode & 0x0F00) >> 8] - registrosV[(opcode & 0x00F0) >> 4]);
        } else {
            registrosV[0xF] = 0;
            registrosV[(opcode & 0x0F00) >> 8] = 256 + (registrosV[(opcode & 0x0F00) >> 8] - registrosV[(opcode & 0x00F0) >> 4]);
        }

        programCounter += 2;
        //System.out.println("Opcode: " + getOpcodeAsString(opcode) + " " + getCPU_StatusAsString());
    }

    private void Ox8XY6() {
        //Setea VF = 1 o 0 según bit menos significativo de VX. Divide VX por 2.

        registrosV[(opcode & 0x0F00) >> 8] &= 0xFF;

        registrosV[0xF] = registrosV[(opcode & 0x0F00) >> 8] & 0x1;

        // Division por 2 usando Shift-right un lugar.
        registrosV[(opcode & 0x0F00) >> 8] = (registrosV[(opcode & 0x0F00) >> 8] >>> 1);
        programCounter += 2;
        //System.out.println("Opcode: " + getOpcodeAsString(opcode) + " " + getCPU_StatusAsString());
    }

    private void Ox8XY7() {
        //VX = VY - VX
        //Si VY >= VX => VF = 1, sino 0. VX = VY - VX.
        //(Nota: Revisar el signo de igualdad: deberia ser la misma condicion que la instruccion 8XY5)

        registrosV[(opcode & 0x0F00) >> 8] &= 0xFF;
        registrosV[(opcode & 0x00F0) >> 4] &= 0xFF;

        if (registrosV[(opcode & 0x0F00) >> 8] < (registrosV[(opcode & 0x00F0) >> 4])) {
            registrosV[0xF] = 1;
            registrosV[(opcode & 0x0F00) >> 8] = registrosV[(opcode & 0x00F0) >> 4] - registrosV[(opcode & 0x0F00) >> 8];
        } else {
            registrosV[0xF] = 0;
            registrosV[(opcode & 0x0F00) >> 8] = 256 + registrosV[(opcode & 0x00F0) >> 4] - registrosV[(opcode & 0x0F00) >> 8];
        }

        programCounter += 2;
        //System.out.println("Opcode: " + getOpcodeAsString(opcode) + " " + getCPU_StatusAsString());
    }

    private void Ox8XYE() {
        //Establece VF = 1 o 0 según bit más significativo de VX. Multiplica VX por 2.
        registrosV[(opcode & 0x0F00) >> 8] &= 0xFF;

        int bit = (registrosV[(opcode & 0x0F00) >> 8]) & 0x80;

        if (bit != 0) {
            bit = 1;
        }

        registrosV[0xF] = bit;

        // Multiplicacion por 2 usando Shift-left un lugar.
        registrosV[(opcode & 0x0F00) >> 8] = (registrosV[(opcode & 0x0F00) >> 8] << 1) & 0xFF;
        programCounter += 2;
        //System.out.println("Opcode: " + getOpcodeAsString(opcode) + " " + getCPU_StatusAsString());
    }

    private void Ox9XY0() {
        //Se saltea la siguiente instrucción si VX != VY.

        // Ejecutar Opcode (Execute Opcode)
        registrosV[opcode & 0x0F00 >> 8] &= 0xFF;
        registrosV[opcode & 0x00F0 >> 4] &= 0xFF;

        if ((registrosV[opcode & 0x0F00 >> 8]) != (registrosV[opcode & 0x00F0 >> 4])) {
            programCounter += 4;
        } else {
            programCounter += 2;
        }
        //System.out.println("Opcode: " + getOpcodeAsString(opcode) + " " + getCPU_StatusAsString());
    }

    private void OxANNN() {
        //Establece registroIndice = NNN.

        // Ejecutar Opcode (Execute Opcode)
        registroIndice = (opcode & 0x0FFF);
        programCounter += 2;
        //System.out.println("Opcode: " + getOpcodeAsString(opcode) + " " + getCPU_StatusAsString());
    }

    private void OxBNNN() {
        //Salta a la ubicación V0 + NNN.

        // Ejecutar Opcode (Execute Opcode)
        registrosV[0x0] &= 0xFF;
        programCounter = registrosV[0x0] + (opcode & 0x0FFF);
        //System.out.println("Opcode: " + getOpcodeAsString(opcode) + " " + getCPU_StatusAsString());
    }

    private void OxCXNN() {
        //Setea VX = un Byte Aleatorio AND NN.

        // Ejecutar Opcode (Execute Opcode)
        registrosV[opcode & 0x0F00 >> 8] = ((opcode & 0x00FF) & (rand.nextInt(256)));
        programCounter += 2;
        //System.out.println("Opcode: " + getOpcodeAsString(opcode) + " " + getCPU_StatusAsString());
    }

    private void OxDXYN() {
        /**
         * Implementacion de
         * http://www.multigesture.net/articles/how-to-write-an-emulator-chip-8-interpreter/
         * Autor: Laurence Muller. Modificado por Diego Gutierrez - 2022.
         * Descripcion: Pinta un sprite en la pantalla. El interprete lee N
         * bytes desde la memoria, comenzando desde el contenido del registro I.
         * Y se muestra dicho byte en las posiciones VX, VY de la pantalla. A
         * los sprites que se pintan se le aplica XOR con lo que está en
         * pantalla. Si esto causa que algún pixel se borre, el registro VF se
         * setea a 1, de otra forma se setea a 0. Si el sprite se posiciona
         * afuera de las coordenadas de la pantalla, dicho sprite se le hace
         * aparecer en el lado opuesto de la pantalla. Notas: el sprite a
         * mostrar se encuentra almacenado en memoria y apuntado por el registro
         * Indice (I)
         *
         * Notas: Marzo 2022 - Se modifica la implementacion a fin de hacerla
         * mas sencilla utilizando una matriz 64x32 en lugar de un arreglo de
         * tamaño 64x32 (Diego Gutierrez)
         */

        int x = ((registrosV[(opcode & 0x0F00) >> 8]) & 0xFF);
        int y = ((registrosV[(opcode & 0x00F0) >> 4]) & 0xFF);
        int height = ((opcode & 0x000F) & 0xFF);
        int pixel;

        int xline, yline;
        int xp = x;
        int yp = y;

        registrosV[0xF] = 0;

        for (yline = 0; yline < height; yline++) {
            pixel = ((memoria[registroIndice + yline]) & 0xFF);
            for (xline = 0; xline < 8; xline++) {
                if ((pixel & (0x80 >> xline)) != 0) {
                    // Verificar que siempre se este dentro del rango del arreglo (agregado el 06/03/2022)

                    if (GFX[(xp + xline) % WIDTH][(yp + yline) % HEIGHT] == 1) {
                        registrosV[0xF] = 1;
                    }
                    GFX[(xp + xline) % WIDTH][(yp + yline) % HEIGHT] ^= 1;

                }
            }
        }

        drawFlag = true;
        pantalla = screen.renderizarPantalla(GFX);
        programCounter += 2;
        //System.out.println("Opcode: " + getOpcodeAsString(opcode) + " " + getCPU_StatusAsString());
    }

    private void OxEX9E() {
        // EX9E: Skips the next instruction if the key stored in VX is pressed.
        if (keyboard[registrosV[(opcode & 0x0F00) >> 8]] != 0) {
            programCounter += 4;
        } else {
            programCounter += 2;
        }
        //System.out.println("Opcode: " + getOpcodeAsString(opcode) + " " + getCPU_StatusAsString());
    }

    private void OxEXA1() {
        // EXA1: Skips the next instruction if the key stored in VX is not pressed.
        if (keyboard[registrosV[(opcode & 0x0F00) >> 8]] == 0) {
            programCounter += 4;
        } else {
            programCounter += 2;
        }
        //System.out.println("Opcode: " + getOpcodeAsString(opcode) + " " + getCPU_StatusAsString());
    }

    private void OxFX07() {
        // FX07: Setea Vx = valor del delay timer.
        delay_Timer &= 0xFF;
        registrosV[(opcode & 0x0F00) >> 8] = delay_Timer;
        programCounter += 2;
        //System.out.println("Opcode: " + getOpcodeAsString(opcode) + " " + getCPU_StatusAsString());
    }

    private void OxFX0A() {
        // FX0A: Espera por una tecla presionada y la almacena en el registro.
        //Implementacion basada en la de Laurence Muller.
        //Reimplementacion adaptada a Java por Diego Gutierrez - 2022
        teclaPresionada = false;
        detectarTecla();

        // If we didn't received a keypress, skip this cycle and try again.
        if (!(teclaPresionada)) {
            return;
        }
        programCounter += 2;
        //System.out.println("Opcode: " + getOpcodeAsString(opcode) + " " + getCPU_StatusAsString());
    }

    private void OxFX15() {
        // FX15: Establecer el delay timer a VX
        registrosV[(opcode & 0x0F00) >> 8] &= 0xFF;
        delay_Timer = registrosV[(opcode & 0x0F00) >> 8];
        programCounter += 2;
        //System.out.println("Opcode: " + getOpcodeAsString(opcode) + " " + getCPU_StatusAsString());
    }

    private void OxFX18() {
        // FX18: Establecer el sound timer a VX
        registrosV[(opcode & 0x0F00) >> 8] &= 0xFF;
        sound_Timer = registrosV[(opcode & 0x0F00) >> 8];
        programCounter += 2;
        //System.out.println("Opcode: " + getOpcodeAsString(opcode) + " " + getCPU_StatusAsString());
    }

    private void OxFX1E() {
        // FX1E: Suma VX a I
        // VF se establece a 1 cuando existe overflow de rango (registroIndice + VX > 0xFFF), y 0 cuando no se produce.

        registroIndice &= 0x0FFF;
        registrosV[(opcode & 0x0F00) >> 8] &= 0xFF;

        if ((registroIndice + registrosV[(opcode & 0x0F00) >> 8]) > 0xFFF) {
            registrosV[0xF] = 1;
        } else {
            registrosV[0xF] = 0;
        }

        registroIndice = (registroIndice + registrosV[(opcode & 0x0F00) >> 8]) & 0x0FFF;

        programCounter += 2;
        //System.out.println("Opcode: " + getOpcodeAsString(opcode) + " " + getCPU_StatusAsString());
    }

    private void OxFX29() {
        // FX29: Set I to the memory address of the sprite data corresponding to the hexadecimal digit stored in register VX
        //Characters 0-F (in hexadecimal) are represented by a 4x5 font
        registroIndice &= 0x0FFF;
        registrosV[(opcode & 0x0F00) >> 8] &= 0xFF;

        registroIndice = ((registrosV[(opcode & 0x0F00) >> 8]) * 0x5);
        //System.out.println("Indice de caracter: " + (registrosV[(opcode & 0x0F00) >> 8] * 0x5));
        //System.out.println("Valor del registro: " + (registrosV[(opcode & 0x0F00) >> 8]));
        programCounter += 2;
        //System.out.println("Opcode: " + getOpcodeAsString(opcode) + " " + getCPU_StatusAsString());
    }

    private void OxFX33() {
        // FX33: Stores the Binary-coded decimal representation of VX at the addresses I, I plus 1, and I plus 2

        if (registrosV[(opcode & 0x0F00) >> 8] >= 0) {
            registrosV[(opcode & 0x0F00) >> 8] &= 0xFF;
        } else {
            registrosV[(opcode & 0x0F00) >> 8] = (registrosV[(opcode & 0x0F00) >> 8] + 256) & 0xFF;
        }

        memoria[registroIndice] = ((registrosV[(opcode & 0x0F00) >> 8] / 100)) & 0xFF;
        memoria[registroIndice + 1] = ((registrosV[(opcode & 0x0F00) >> 8] / 10) % 10) & 0xFF;
        memoria[registroIndice + 2] = ((registrosV[(opcode & 0x0F00) >> 8] % 100) % 10) & 0xFF;

        //System.out.println("Centenas: " + (registrosV[(opcode & 0x0F00) >> 8] / 100));
        //System.out.println("Decenas: " + (registrosV[(opcode & 0x0F00) >> 8] / 10) % 10);
        //System.out.println("Unidades: " + (registrosV[(opcode & 0x0F00) >> 8] % 100) % 10);
        //System.out.println("Valor del registro: " + (registrosV[(opcode & 0x0F00) >> 8]));
        programCounter += 2;
        //System.out.println("Opcode: " + getOpcodeAsString(opcode) + " " + getCPU_StatusAsString());
    }

    private void OxFX55() {

        for (int i = 0; i <= ((opcode & 0x0F00) >> 8); ++i) {
            memoria[registroIndice + i] = registrosV[i];
        }

        // En el interprete original, cuando la operacion finaliza, I = I + X + 1.
        //registroIndice = (registroIndice + (((opcode & 0x0F00) >> 8) + 1)) & 0x0FFF;
        //registroIndice = ((registroIndice + ((opcode & 0x0F00) >> 8) + 1) & 0x0FFF);
        programCounter += 2;
        //System.out.println("Opcode: " + getOpcodeAsString(opcode) + " " + getCPU_StatusAsString());
    }

    private void OxFX65() {
        // FX65: Fills V0 to VX with values from memory starting at address I

        for (int i = 0; i <= ((opcode & 0x0F00) >> 8); ++i) {
            registrosV[i] = memoria[registroIndice + i];
        }

        // En el interprete original, cuando la operacion finaliza, I = I + X + 1.
        //registroIndice = (registroIndice + (((opcode & 0x0F00) >> 8) + 1)) & 0x0FFF;
        //registroIndice = (registroIndice + ((opcode & 0x0F00) >> 8) + 1) & 0x0FFF;
        programCounter += 2;
        //System.out.println("Opcode: " + getOpcodeAsString(opcode) + " " + getCPU_StatusAsString());
    }

    private String getOpcodeAsString(int opcode) {
        return Integer.toHexString(opcode);
    }

    private String getCPU_StatusAsString() {

        String status = "";

        for (int i = 0; i <= registrosV.length - 1; i++) {
            status += "V" + i + ": " + Integer.toHexString(registrosV[i]) + " ";
        }

        return status;
    }

    public void run() {
        
        while (true) {
            
            try {

                //System.out.println(singleStep);
                //System.out.println(singleStepKey);
                
                if (singleStep == false) {
                    chip8EmularCiclo();
                }

                /*if (singleStep && singleStepKey) {
                    
                    //System.out.println("Ejecutando bucle en modo single step");
                    chip8EmularCicloSingleStep();

                }*/
            } catch (LineUnavailableException ex) {
                Logger.getLogger(Chip8_CPU.class.getName()).log(Level.SEVERE, null, ex);

            } catch (InterruptedException ex) {
                Logger.getLogger(Chip8_CPU.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
}

/**
 * Clase auxiliar para generar sonido. Referencia:
 * https://stackoverflow.com/questions/34611134/java-beep-sound-produce-sound-of-some-specific-frequencies
 */
class Sound {

    private Sound() {

    }

    static float SAMPLE_RATE = 8000f;

    static void tone(int hz, int msecs) throws LineUnavailableException {
        tone(hz, msecs, 1.0);
    }

    static void tone(int hz, int msecs, double vol) throws LineUnavailableException {
        byte[] buf = new byte[1];
        AudioFormat af = new AudioFormat(SAMPLE_RATE, 8, 1, true, false);
        SourceDataLine sdl = AudioSystem.getSourceDataLine(af);
        sdl.open(af);
        sdl.start();
        for (int i = 0; i < msecs * 8; i++) {
            double angle = i / (SAMPLE_RATE / hz) * 2.0 * Math.PI;
            buf[0] = (byte) (Math.sin(angle) * 127.0 * vol);
            sdl.write(buf, 0, 1);
        }
        sdl.drain();
        sdl.stop();
        sdl.close();
    }
}
