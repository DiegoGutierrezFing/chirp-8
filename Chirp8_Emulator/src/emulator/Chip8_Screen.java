/*
 * Copyright (C) 2022 Diego Andrés Gutiérrez Berón
 * 
 * Some parts of this software were made as adaptations of ideas other than mine.
 * In any case, due credit or references are given for any ideas or code that 
 * served as an implementation base for this software.
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

import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * @author Diego Gutierrez.
 *
 * Febrero, Marzo, Abril 2022.
 */


/**
 * Clase para representar el "frame buffer" (pantalla)
 * Idea basada en codigo de ejemplo encontrado en la referencia
 * Referencia: Deitel - Como programar en C, C++ y Java (libro)
 */
public class Chip8_Screen {

    /**
     * Clase auxiliar para representar el frame buffer (pantalla) Referencia:
     * Deitel: Como programar en C, C++ y Java (libro)
     */
    private int WIDTH = 64;
    private int HEIGHT = 32;
    private BufferedImage pantalla;

    private Color backgroundColor = Color.BLACK;
    private Color foregroundColor = Color.WHITE;

    public Chip8_Screen(int width, int height) {
        pantalla = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    }

    public Chip8_Screen() {
        pantalla = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
    }

    public BufferedImage renderizarPantalla(int[][] GFX) {

        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {

                pantalla.setRGB(x, y, obtenerColor(GFX[x][y]).getRGB());
            }
        }
        return pantalla;
    }

    private Color obtenerColor(int[] GFX, int indice) {
        if (GFX[indice] != 0) {
            return foregroundColor;
        } else {
            return backgroundColor;
        }
    }

    private Color obtenerColor(int pixel) {
        if (pixel != 0) {
            return foregroundColor;
        } else {
            return backgroundColor;
        }
    }

    public int getWIDTH() {
        return WIDTH;
    }

    public void setWIDTH(int WIDTH) {
        this.WIDTH = WIDTH;
    }

    public int getHEIGHT() {
        return HEIGHT;
    }

    public void setHEIGHT(int HEIGHT) {
        this.HEIGHT = HEIGHT;
    }

    public BufferedImage getPantalla() {
        return pantalla;
    }

    public void setPantalla(BufferedImage pantalla) {
        this.pantalla = pantalla;
    }

    public Color getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(Color backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public Color getForegroundColor() {
        return foregroundColor;
    }

    public void setForegroundColor(Color foregroundColor) {
        this.foregroundColor = foregroundColor;
    }
}
