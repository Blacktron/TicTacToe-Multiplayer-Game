package tttserver;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Scanner;
import java.io.PrintWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TTTServer {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws IOException {
        
        final int PORT = 8888;
        ServerSocket server = null;
        Socket client = null;
        ArrayList<TTTServerThread> threads = null;
        Scanner in = null;
        PrintWriter out = null;
        
        ExecutorService pool = Executors.newCachedThreadPool();
        try {
            server = new ServerSocket(PORT);
            threads = new ArrayList<TTTServerThread>();
            
            outer_loop:
            while (true) {
                client = server.accept();
                in = new Scanner(client.getInputStream());
                out = new PrintWriter(client.getOutputStream());
                
                String str = in.nextLine();
                
                if (str.equals("HOST")) {
                    // Create new thread, host waits for a party to join
                    TTTServerThread newT = new TTTServerThread(client);
                    threads.add(newT);
                    pool.execute(newT);
                    System.out.println("New host added.");
                }
                else if (str.equals("JOIN")) {
                    String destIP = in.nextLine();
                    
                    System.out.println("Request from a party to join.");
                    
                    // Check the list with active hosts
                    for (int i = 0; i < threads.size(); i++) {
                        TTTServerThread hostT = threads.get(i);
                        String hostIP = hostT.getIPAddress();
                        
                        // Check if the host is still active
                        if (!hostT.isActive()) {
                            threads.remove(i);
                            --i;
                            continue;
                        }
                        
                        // If the host and destination IPs match and connect the threads if they do
                        if (hostIP.equals(destIP)) {
                            TTTServerThread newT = new TTTServerThread(client);
                            
                            hostT.connect(newT);
                            newT.connect(hostT);
                            
                            pool.execute(newT);
                            threads.remove(i);
                            
                            hostT.begin();
                            newT.begin();
                            
                            continue outer_loop;
                        }
                    }
                    // The requested server is not found
                    out.println("254");
                    out.flush();
                }
            }
        }
        catch (IOException e) {
            System.err.println(e.getMessage());
        }
        finally {
            server.close();
            pool.shutdown();
        }
    }
}
