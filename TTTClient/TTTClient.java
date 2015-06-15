package tttclient;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

public class TTTClient extends JFrame implements ActionListener, WindowListener {

    private JButton[][] gameField;                          // The playing gameField
    private JButton hostGameButton, joinGameButton;     // Main Menu buttons
    private JButton returnMenu;                         // Returning to main menu button
    private JLabel statusLabel;                         // Display current game status
    private JPanel menuPanel, statusPanel, fieldPanel;  // All panels
    private boolean isHost, myTurn, running;            // Flags determining if the player is host, is it current player's turn and is the game running
    private final int FIELD_SIZE = 3;                   // Size of the gameField
    private int moveCount;                              // Counter for player moves
    private String serverAddress;                       // The IP of the host player
    private String role = null;
    
    private final int PORT = 8888;                      // The port used for the socket
    private Socket client = null;                       // Client socket
    private Scanner in = null;                          // Input stream used for reading messages
    private PrintWriter out = null;                     // Output strem used for sending messages
    private Thread linkThread = null;                   // Thread used for the connection
    
    public TTTClient() {
        setTitle("Tic Tac Toe");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        addWindowListener(this);
        
        setSize(400, 400);
        setResizable(false);
        setVisible(true);
        
        // Enter Main Menu
        showMenu();
    }
    
    // Display Main Menu - Host and Join options
    public void showMenu() {
        // Clear all
        getContentPane().removeAll();
        
        menuPanel = new JPanel(new GridLayout(2, 1));
        
        // Create the buttons
        hostGameButton = new JButton("Host Game");
        hostGameButton.addActionListener(this);
        menuPanel.add(hostGameButton, BorderLayout.CENTER);
        
        joinGameButton = new JButton("Join Game");
        joinGameButton.addActionListener(this);
        menuPanel.add(joinGameButton, BorderLayout.SOUTH);
        
        // Display the menu
        add(menuPanel, BorderLayout.CENTER);
        revalidate();
        repaint();
    }
    
    // Display the return to main menu button
    public void returnToMainMenu() {
        returnMenu = new JButton("Main Menu");
        returnMenu.addActionListener(this);
        statusPanel.add(returnMenu, BorderLayout.SOUTH);
    }
    
    // Construct the game gameField
    public void setField() {
        // Setup the gameField
        fieldPanel = new JPanel(new GridLayout(3, 3));
        
        Font gameFont = new Font("Tahoma", Font.PLAIN, 30);
        
        gameField = new JButton[FIELD_SIZE][FIELD_SIZE];
        for (int i = 0; i < FIELD_SIZE; i++) {
            for (int j = 0; j < FIELD_SIZE; j++) {
                gameField[i][j] = new JButton();
                fieldPanel.add(gameField[i][j]);
                gameField[i][j].addActionListener(this);
                gameField[i][j].setPreferredSize(new Dimension(40, 40));
                gameField[i][j].setFont(gameFont);
                gameField[i][j].setForeground(Color.RED);
            }
        }
        
        // Change panels
        remove(menuPanel);
        add(fieldPanel, BorderLayout.CENTER);
        revalidate();
    }
    
    // Start the game
    public void startGame() {
        // Setup the status pabel
        if (isHost) {
            statusLabel = new JLabel("Your turn");
        }
        else {
            statusLabel = new JLabel("Opponent's turn");
        }
        
        statusPanel = new JPanel(new BorderLayout());
        statusPanel.add(statusLabel, BorderLayout.CENTER);
        
        // Add the new elements
        add(statusPanel, BorderLayout.NORTH);
        setField();
        
        moveCount = 0;
    }

    // Host a new game
    public void hostGame() throws IOException {
        // Set the host flag
        isHost = true;
        
        startGame();
        
        statusLabel.setText("Waiting for a player to join.");
        
	role = "HOST";
        openConnection();
    }
    
    // Join to a game
    public void joinGame() throws IOException {
        // Set the host flag
        isHost = false;
        
        serverAddress = (String)JOptionPane.showInputDialog(this, "Enter the address of the server:", "Connect to host", JOptionPane.PLAIN_MESSAGE);
        
	role = "JOIN";
        openConnection();
        
        startGame();
    }
    
    // Open a socket and wait for connection
    public void openConnection() {
        // Start a new thread, otherwise the GUI will be blocked
        linkThread = new Thread() {
            public void run() {
                try {
                    client = new Socket("127.0.0.1", PORT);
                    in = new Scanner(client.getInputStream());
                    out = new PrintWriter(client.getOutputStream());
					
                    if(role.equals("HOST")) {
                        out.println("HOST");
                        out.flush();
                    } else {
                        out.println("JOIN");
                        out.println(serverAddress);
                        out.flush();
                    }
                    
                    while (true) {
                        // Stop if have to
                        if (interrupted()) return;
                        
                        Thread.sleep(100);
                        
                        // Do nothing if there are no actions
                        if (!in.hasNext()) continue;
                        
                        String input = in.next();
                        if (input.equals("BEGIN")) {
                            running = true;
                            
                            if (role.equals("HOST")) {
                                myTurn = true;
                                statusLabel.setText("Your turn!");
                            }
                        }
                        else if (input.equals("GO")) {
                            int x = in.nextInt();
                            int y = in.nextInt();
                            
                            placeTic(x, y, false);
                        }
                        else if (input.equals("254")) {
                            JOptionPane.showMessageDialog (null, "No host with that IP was found!", "Error 254", JOptionPane.INFORMATION_MESSAGE);
                            showMenu();
                        }
			else if (input.equals("354")) {
                            JOptionPane.showMessageDialog (null, "The other party has closed the application! You win!", "Error 354", JOptionPane.INFORMATION_MESSAGE);
                            showMenu();
			}
                    }
                }
                catch (IOException e) {
                    System.err.println("openConnection (IOException): " + e.getMessage());
                }
                catch (InterruptedException e) {
                    System.err.println("openConnection (InterruptedException): " + e.getMessage());
                }
            }
        };
        linkThread.start();
    }
    
    // Play at the given coordinates, if isMe is true - the move was made by the local party, otherwise the move was made by the remote party
    public void placeTic(int x, int y, boolean isMe) {
        // Check if that gameField is already taken
        if (!gameField[x][y].getText().equals("")) return;
        
        // Set the mark and color to be used
        String mark;
        Color markColor;
        
        if (isMe && isHost) {
            mark = "X";
            markColor = Color.blue;
        }
        else if (isMe && !isHost) {
            mark = "O";
            markColor = Color.yellow;
        }
        else if (!isMe && isHost) {
            mark = "O";
            markColor = Color.yellow;
        }
        else {
            mark = "X";
            markColor = Color.blue;
        }
        
        // Place the mark
        gameField[x][y].setText(mark);
        gameField[x][y].setBackground(markColor);
        
        // Reverse turn
        if (isMe) {
            myTurn = false;
            statusLabel.setText("Opponent's turn.");
            
            // Inform the other party for the move
            out.println("GO " + x + " " + y);
            out.flush();
        }
        else {
            myTurn = true;
            statusLabel.setText("Your turn.");
        }

        // Check if someone won the game with the last move
        if (checkWin(x, y)) {
            if (isMe) {
                statusLabel.setText("You won.");
            }
            else {
                statusLabel.setText("Opponent won.");
            }
            
            running = false;
            returnToMainMenu();
            closeConnection();
        }
        
        moveCount++;
        
        // Check for a draw
        if (moveCount == 9) {
            statusLabel.setText("Draw");
            
            running = false;
            returnToMainMenu();
            closeConnection();
        }
    }
    
    // Check if the last move formed a line
    public boolean checkWin(int x, int y) {
        // Save the last move
        String temp = gameField[x][y].getText();
        
        boolean check = true;
        
        // Check the rows
        for (int i = 0; i < FIELD_SIZE; i++) {
            if (!temp.equals(gameField[x][i].getText())) {
                check = false;
                break;
            }
        }
        
        // Did all marks in the row match
        if (check) return true;
        else check = true;
        
        // Check the columns
        for (int i = 0; i < FIELD_SIZE; i++) {
            if (!temp.equals(gameField[i][y].getText())) {
                check = false;
                break;
            }
        }
        
        // Did all marks in the column match
        if (check) return true;
        else check = true;
        
        // Check one of the diagonals
        for (int i = 0; i < FIELD_SIZE; i++) {
            if (!temp.equals(gameField[i][i].getText())) {
                check = false;
                break;
            }
        }
        
        // Did all marks in the diagonal match
        if (check) return true;
        else check = true;
        
        // Check the other diagonal
        for (int i = 0; i < FIELD_SIZE; i++) {
            if (!temp.equals(gameField[(FIELD_SIZE - 1) - i][i].getText())) {
                check = false;
                break;
            }
        }
        
        // Match was found or nobody won
        if (check) return true;
        else return false;
    }
    
    public void closeConnection() {
        // Stop the thread responsible for the connection
        if (linkThread != null) {
            linkThread.interrupt();
            linkThread = null;
        }
        
        // Release the resources
        try {
            if (out != null) {
                out.close();
                out = null;
            }
            if (in != null) {
                in.close();
                in = null;
            }
            if (client != null) {
                client.close();
                client = null;
            }
        }
        catch (IOException e) {
            System.out.println("Could not close the connection!");
        }
    }
    
    @Override
    public void actionPerformed(ActionEvent ae) {
        try {
            if (ae.getSource() == hostGameButton) {
                hostGame();
                return;
            }
            else if (ae.getSource() == joinGameButton) {
                joinGame();
                return;
            }
            else if (ae.getSource() == returnMenu) {
                showMenu();
                return;
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        
        // Check if the game is running
        if (!myTurn || !running) return;
        
        // Play at the selected box
        for (int i = 0; i < FIELD_SIZE; i++) {
            for (int j = 0; j < FIELD_SIZE; j++) {
                if (ae.getSource() == gameField[i][j]) {
                    placeTic(i, j, true);
                    return;
                }
            }
        }
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        java.awt.EventQueue.invokeLater(new Runnable() {

            @Override
            public void run() {
                new TTTClient();
            }
        });
    }    

    @Override
    public void windowOpened(WindowEvent we) { }

    @Override
    public void windowClosing(WindowEvent we) {
        // Inform the server that the client closed the window
        if(out != null) {
            out.println("354");
            out.flush();
        }

        // Stop the link thread
        if(linkThread != null) { linkThread.interrupt(); }

        // Free resources
        try {
            if(client != null) { client.close(); }
            if(out != null) { out.close(); }
            if(in != null) { in.close();}
        } catch (IOException exc) {
            exc.printStackTrace();
        }
    }

    @Override
    public void windowClosed(WindowEvent we) { }

    @Override
    public void windowIconified(WindowEvent we) { }

    @Override
    public void windowDeiconified(WindowEvent we) { }

    @Override
    public void windowActivated(WindowEvent we) { }

    @Override
    public void windowDeactivated(WindowEvent we) { }
}
