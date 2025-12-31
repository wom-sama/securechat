/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.securechat.securechat;

import com.securechat.ui.LoginForm;

import javax.swing.*;

public class App {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (ClassNotFoundException | IllegalAccessException | InstantiationException | UnsupportedLookAndFeelException ignored) {}
            new LoginForm().setVisible(true);
        });
    }
}
