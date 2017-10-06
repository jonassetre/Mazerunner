package mazeoblig;

import simulator.PositionInMaze;
import simulator.VirtualUser;

import java.awt.*;
import java.applet.*;


/**
 *
 * <p>Title: Maze</p>
 *
 * <p>Description: En enkel applet som viser den randomiserte labyrinten</p>
 *
 * <p>Copyright: Copyright (c) 2006</p>
 *
 * <p>Company: </p>
 *
 * @author not attributable
 * @version 1.0
 */
import java.rmi.RemoteException;
import java.rmi.NotBoundException;
import java.util.HashMap;

import static java.lang.Thread.sleep;

/**
 * Tegner opp maze i en applet, basert p� definisjon som man finner p� RMIServer
 * RMIServer p� sin side  henter st�rrelsen fra definisjonen i Maze
 *
 * @author asd
 */
@SuppressWarnings("serial")
public class Maze extends Applet {

    private BoxMazeInterface bm;
    private Box[][] maze;
    public static int DIM = 30;
    private int dim = DIM;
    private String server_hostname;
    private int server_portnumber;
    private ServerInterface serverInterface;
    private HashMap clientPositions = null;
    private HashMap clientColors = null;
    private final int CLIENTS_TO_CREATE = 20;
    private Integer mapDrawingClientId;


    /**
     * Establish server and registry connection. Retrieve all remote objects from RMI server
     */
    public void init() {
        int size = dim;
        /*
		 ** Kobler opp mot RMIServer, under forutsetning av at disse
		 ** kj�rer p� samme maskin. Hvis ikke m� oppkoblingen
		 ** skrives om slik at dette passer med virkeligheten.
		 */
        if (server_hostname == null)
            server_hostname = RMIServer.getHostName();
        if (server_portnumber == 0)
            server_portnumber = RMIServer.getRMIPort();
        try {
            java.rmi.registry.Registry r = java.rmi.registry.LocateRegistry.
                    getRegistry(server_hostname,
                            server_portnumber);

			/*
			 ** Henter inn referansen til Labyrinten (ROR)
			 */
            bm = (BoxMazeInterface) r.lookup(RMIServer.MazeName);
            maze = bm.getMaze();

            //Henter referansen til ServerInterface metoder
            serverInterface = (ServerInterface) r.lookup(RMIServer.talkToServerIdString);

        } catch (RemoteException e) {
            System.err.println("Remote Exception: " + e.getMessage());
            System.exit(0);
        } catch (NotBoundException f) {
			/*
			 ** En exception her er en indikasjon p� at man ved oppslag (lookup())
			 ** ikke finner det objektet som man s�ker.
			 ** �rsaken til at dette skjer kan v�re mange, men v�r oppmerksom p�
			 ** at hvis hostname ikke er OK (RMIServer gir da feilmelding under
			 ** oppstart) kan v�re en �rsak.
			 */
            System.err.println("Not Bound Exception: " + f.getMessage());
            System.exit(0);
        }
    }

    /**
     * Creates clients, mapupdater and populates maze
     */
    public void start() {

        for (int i = 0; i < CLIENTS_TO_CREATE; i++) {

            try {
                new CreateClient().start();
                sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        new RequestMapUpdate().start();
    }

    /**
     * Render the maze and registered clients
     */
    public void paint(Graphics g) {
        int x, y;

        //clear the previous map
        g.clearRect(0, 0, getWidth(), getHeight());

        // Draw the map

        for (x = 1; x < (dim - 1); ++x)
            for (y = 1; y < (dim - 1); ++y) {
                if (maze[x][y].getUp() == null)
                    g.drawLine(x * 10, y * 10, x * 10 + 10, y * 10);
                if (maze[x][y].getDown() == null)
                    g.drawLine(x * 10, y * 10 + 10, x * 10 + 10, y * 10 + 10);
                if (maze[x][y].getLeft() == null)
                    g.drawLine(x * 10, y * 10, x * 10, y * 10 + 10);
                if (maze[x][y].getRight() == null)
                    g.drawLine(x * 10 + 10, y * 10, x * 10 + 10, y * 10 + 10);
            }

            //if client positions does exist, render the clients into the map
        if (clientPositions != null) {

            //draw client positions
            clientPositions.forEach((key, value) -> {

                PositionInMaze pos = (PositionInMaze) value;

                g.drawOval(pos.getXpos() * 10, pos.getYpos() * 10, 10, 10);

                //render client colors if possible
                if (clientColors.containsKey(key)) {
                    g.setColor((Color) clientColors.get(key));
                    g.fillOval(pos.getXpos() * 10, pos.getYpos() * 10, 10, 10);
                }
            });
        }
    }

    /**
     * class used for creating n clients using threads. One of these clients (which one doesn't matter)
     * will be the mapdrawer, responsible for periodically rendering the map
     */
    private class CreateClient extends Thread {

        CreateClient() {}

        public void run() {

            try {
                VirtualUser user = new VirtualUser(maze, serverInterface);

                //assign a random mapdrawerclient
                if (mapDrawingClientId == null) {
                    mapDrawingClientId = user.getClientId();
                }
                user.sendClientColor();

                //keep sending updated positions to server
                while (true) {
                    user.sendClientPosition();

                    //if the user is the mapdrawerclient, copy the newest clientpositions to Maze class, and repaint
                    //the map
                    if (user.getClientId().equals(mapDrawingClientId)) {
                        clientPositions = user.getListOfAllPosition();
                        repaint();
                    }

                    //timeout so clients do not move too quickly
                    try {
                        sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Receives client color properties. Periodically signals the server to send stored client positions to all clients
     */
    private class RequestMapUpdate extends Thread {
        public void run() {
            try {
                clientColors = serverInterface.requestClientColors();

                while (true) {
                    sleep(100);
                    serverInterface.sendAllClientPositions();
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}