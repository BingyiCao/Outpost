package outpost.sim;

// general utilities
import java.io.*;
import java.util.List;
import java.util.*;

import javax.tools.*;

import java.util.concurrent.*;
import java.net.URL;

// gui utility
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;

import javax.swing.*;

import outpost.sim.Pair;
import outpost.sim.Player;
import outpost.sim.Point;

public class Outpost
{
    static String ROOT_DIR = "outpost";

    // recompile .class file?
    static boolean recompile = true;
    
    // print more details?
    static boolean verbose = true;

    // Step by step trace
    static boolean trace = true;

    // enable gui
    static boolean gui = true;
    
    static int size =100;
    static private Point[] grid = new Point[size*size];
    
    static int r_distance;
    
    static boolean reach = false;
    static Player[] players = new Player[4];
   
    static int L; //land cells number to afford outpost
    static int W; 
    
    static int MAX_TICKS = 10000;
    static ArrayList<Pair> searchlist = new ArrayList();
    
    static double[] water = new double[4];
	static double[] soil = new double[4];
	static int[] noutpost = new int[4];
	static int nrounds;
	
	static ArrayList<ArrayList<Pair>> king_outpostlist = new ArrayList<ArrayList<Pair>>();
    
	// list files below a certain directory
	// can filter those having a specific extension constraint
    //
	static List <File> directoryFiles(String path, String extension) {
		List <File> allFiles = new ArrayList <File> ();
		allFiles.add(new File(path));
		int index = 0;
		while (index != allFiles.size()) {
			File currentFile = allFiles.get(index);
			if (currentFile.isDirectory()) {
				allFiles.remove(index);
				for (File newFile : currentFile.listFiles())
					allFiles.add(newFile);
			} else if (!currentFile.getPath().endsWith(extension))
				allFiles.remove(index);
			else index++;
		}
		return allFiles;
	}

  	// compile and load players dynamically
    //
	static Player loadPlayer(String group, int id) {
        try {
            // get tools
            URL url = Outpost.class.getProtectionDomain().getCodeSource().getLocation();
            // use the customized reloader, ensure clearing all static information
            ClassLoader loader = new ClassReloader(url, Outpost.class.getClassLoader());
            if (loader == null) throw new Exception("Cannot load class loader");
            JavaCompiler compiler = null;
            StandardJavaFileManager fileManager = null;
            // get separator
            String sep = File.separator;
            // load players
            // search for compiled files
            File classFile = new File(ROOT_DIR + sep + group + sep + "Player.class");
            System.err.println(classFile.getAbsolutePath());
            if (!classFile.exists() || recompile) {
                // delete all class files
                List <File> classFiles = directoryFiles(ROOT_DIR + sep + group, ".class");
                System.err.print("Deleting " + classFiles.size() + " class files...   ");
                for (File file : classFiles)
                    file.delete();
                System.err.println("OK");
                if (compiler == null) compiler = ToolProvider.getSystemJavaCompiler();
                if (compiler == null) throw new Exception("Cannot load compiler");
                if (fileManager == null) fileManager = compiler.getStandardFileManager(null, null, null);
                if (fileManager == null) throw new Exception("Cannot load file manager");
                // compile all files
                List <File> javaFiles = directoryFiles(ROOT_DIR + sep + group, ".java");
                System.err.print("Compiling " + javaFiles.size() + " source files...   ");
                Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromFiles(javaFiles);
                boolean ok = compiler.getTask(null, fileManager, null, null, null, units).call();
                if (!ok) throw new Exception("Compile error");
                System.err.println("OK");
            }
            // load class
            System.err.print("Loading player class...   ");
            Class playerClass = loader.loadClass(ROOT_DIR + "." + group + ".Player");
            System.err.println("OK");
            // set name of player and append on list
            //Player player = (Player) playerClass.newInstance(id);
            Class[] cArg = new Class[1]; //Our constructor has 3 arguments
            //cArg[0] = Pair.class; //First argument is of *object* type Long
            cArg[0] = int.class; //Second argument is of *object* type String
            Player player = (Player) playerClass.getDeclaredConstructor(cArg).newInstance(id);
            if (player == null)
                throw new Exception("Load error");
            else
                return player;
            	
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return null;
        }

	}



    // compute Euclidean distance between two points
    static double distance(Point a, Point b) {
        return Math.sqrt((a.x-b.x) * (a.x-b.x) +
                         (a.y-b.y) * (a.y-b.y));
    }

    static double vectorLength(double ox, double oy) {
        return Math.sqrt(ox * ox + oy * oy);
    }

    void playgui() {
        SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    OutpostUI ui  = new OutpostUI();
                    ui.createAndShowGUI();
                }
            });
    }


    class OutpostUI extends JPanel implements ActionListener {
        int FRAME_SIZE = 800;
        int FIELD_SIZE = 600;
        JFrame f;
        FieldPanel field;
        JButton next;
        JButton next10;
        JButton next50;
        JLabel label;

        public OutpostUI() {
            setPreferredSize(new Dimension(FRAME_SIZE, FRAME_SIZE));
            setOpaque(false);
        }

        public void init() {}

        private boolean performOnce() {
            if (tick > MAX_TICKS) {
                label.setText("Time out!!!");
                label.setVisible(true);
                // print error message
                System.err.println("[ERROR] The player is time out!");
                next.setEnabled(false);
                return false;
            }

            else {
                playStep();
                return true;
            }
        }

        public void actionPerformed(ActionEvent e) {
            int steps = 0;

            if (e.getSource() == next)
                steps = 1;
            else if (e.getSource() == next10)
                steps = 10;
            else if (e.getSource() == next50)
                steps = 50;

            for (int i = 0; i < steps; ++i) {
                if (!performOnce())
                    break;
            }

            repaint();
        }


        public void createAndShowGUI()
        {
            this.setLayout(null);

            f = new JFrame("Outposts");
            field = new FieldPanel(1.0 * FIELD_SIZE / dimension);
            next = new JButton("Next"); 
            next.addActionListener(this);
            next.setBounds(0, 0, 100, 50);
            next10 = new JButton("Next10"); 
            next10.addActionListener(this);
            next10.setBounds(100, 0, 100, 50);
            next50 = new JButton("Next50"); 
            next50.addActionListener(this);
            next50.setBounds(200, 0, 100, 50);

            label = new JLabel();
            label.setVisible(false);
            label.setBounds(0, 60, 200, 50);
            label.setFont(new Font("Arial", Font.PLAIN, 15));

            field.setBounds(100, 100, FIELD_SIZE + 50, FIELD_SIZE + 50);

            this.add(next);
            this.add(next10);
            this.add(next50);
            this.add(label);
            this.add(field);

            f.add(this);

            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.pack();
            f.setVisible(true);
        }

        @Override
        public void paintComponent(Graphics g) {
            super.paintComponent(g);
        }

    }

    class FieldPanel extends JPanel {
        double PSIZE = 10;
        double s;
        BasicStroke stroke = new BasicStroke(2.0f);
        double ox = 10.0;
        double oy = 10.0;

        public FieldPanel(double scale) {
            setOpaque(false);
            s = scale;
        }

        @Override
        public void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g;
            g2.setStroke(stroke);
            
         // draw 2D rectangle
            double x_in = (dimension*s-ox)/size;
            double y_in = (dimension*s-oy)/size;
           // g2.draw(new Rectangle2D.Double(ox,oy,ox+x_in,oy+y_in));
            for (int i=0; i<size; i++) {
            	
            for (int j=0; j<size; j++) {
            	if (grid[i*size+j].water) {
            		g2.setPaint(Color.blue);
            	}
            	else {
            		g2.setPaint(Color.orange);
            	}
            	g2.fill(new Rectangle2D.Double(ox+x_in*i,oy+y_in*j,x_in,y_in));
            	if (grid[i*size+j].ownerlist.size()>0) {
            	//	System.out.println("this is owned by player 0");
            		if (grid[i*size+j].ownerlist.get(0).x==0)  {
            			
            			g2.setPaint(Color.white);
            			g2.fill(new Rectangle2D.Double(ox+x_in/4+x_in*i,oy+x_in/4+y_in*j,x_in/2,y_in/2));
            		}
            		if (grid[i*size+j].ownerlist.get(0).x==1)  {
            			
            			g2.setPaint(Color.MAGENTA);
            			g2.fill(new Rectangle2D.Double(ox+x_in/4+x_in*i,oy+x_in/4+y_in*j,x_in/2,y_in/2));
            		}
            		if (grid[i*size+j].ownerlist.get(0).x==2)  {
            			
            			g2.setPaint(Color.BLACK);
            			g2.fill(new Rectangle2D.Double(ox+x_in/4+x_in*i,oy+x_in/4+y_in*j,x_in/2,y_in/2));
            		}
            		if (grid[i*size+j].ownerlist.get(0).x==3)  {
            			
            			g2.setPaint(Color.RED);
            			g2.fill(new Rectangle2D.Double(ox+x_in/4+x_in*i,oy+x_in/4+y_in*j,x_in/2,y_in/2));
            		}
            	}
            }
            
            }
        
            for (int i = 0; i < king_outpostlist.size(); i++) {
           // drawPoint(g2, pointers[i]);
            	for (int j=0; j<king_outpostlist.get(i).size(); j++) {
            		//System.out.printf("(%d, %d)", i, j);
            		drawPoint(g2, king_outpostlist.get(i).get(j), i);
            	}
            }
            
          
        }
        
        public void drawPoint(Graphics2D g2, Pair pr, int id) {
        	/*if (p.owner==-1) {
                g2.setPaint(Color.BLACK);
        	}
            else {
                g2.setPaint(Color.WHITE);
            }*/
        	if (id == 0) 
                g2.setPaint(Color.WHITE);
            else if (id == 1)
                g2.setPaint(Color.MAGENTA);
            else if (id == 2)
                g2.setPaint(Color.BLACK);
            else if (id == 3)
            	g2.setPaint(Color.RED);
            
        	double x_in = (dimension*s-ox)/size;
            double y_in = (dimension*s-oy)/size;
            
            Ellipse2D e = new Ellipse2D.Double(pr.x*x_in+10, pr.y*y_in+10, x_in, y_in);
            g2.setStroke(stroke);
            g2.draw(e);
            g2.fill(e);
           
        }
        

    
    }



    void updatePoint(Point pr) {
    	pr.ownerlist.clear();
        double mindist = Math.sqrt(size*size);
        
        //ArrayList<Pair> ownerlist = new ArrayList<Pair>();
       
       
        	for (int j =0 ; j<king_outpostlist.size(); j++) {
        		for (int f =0; f<king_outpostlist.get(j).size(); f++) {
        			double d = distance(PairtoPoint(king_outpostlist.get(j).get(f)), pr);
        			if (d <= mindist) {
        				mindist = d;
        				
        			}
        		}
        	}
        if (mindist < r_distance){
        	//ownerlist = pr.ownerlist;
			//ownerlist.clear();
        	//pr.ownerlist.clear();
        	for (int j =0 ; j<king_outpostlist.size(); j++) {
        		for (int f =0; f<king_outpostlist.get(j).size(); f++) {
        			double d = distance(PairtoPoint(king_outpostlist.get(j).get(f)), pr);
        			if (d == mindist) {
        				Pair tmp = new Pair(j, f);
        				if (pr.ownerlist.size()==0) {
        				pr.ownerlist.add(tmp);
        				}
        				else if (!thesameowner(pr, tmp)) {
        					pr.ownerlist.add(tmp);
        				}
        			}
        		}
        	}
        }
    }
    boolean thesameowner(Point pt, Pair tmp) {
    	for (int i=0; i<pt.ownerlist.size(); i++){
    		if (pt.ownerlist.get(i).x==tmp.x)
    			return true;
    	}
    	return false;
    }


    void calculateres() {
    	System.out.println("calculate resouce");
    	for (int i=0; i<4; i++) {
    		water[i] =0.0;
    		soil[i] =0.0;
    	}
    	for (int i=0; i<size*size; i++) {
    			if (grid[i].ownerlist.size() == 1) {
    				if (grid[i].water) {
    					water[grid[i].ownerlist.get(0).x]++;
    				}
    				else {
    					soil[grid[i].ownerlist.get(0).x]++;
    				}
    			}
    			else if (grid[i].ownerlist.size() > 1){
    				for (int f=0; f<grid[i].ownerlist.size(); f++) {
    					if (grid[i].water) {
        					water[grid[i].ownerlist.get(f).x]=water[grid[i].ownerlist.get(f).x]+1/grid[i].ownerlist.size();
        				}
        				else {
        					soil[grid[i].ownerlist.get(f).x]=soil[grid[i].ownerlist.get(f).x]+1/grid[i].ownerlist.size();
        				
        				}
    				}
    				
    			}
    		}
    	for (int i=0; i<4; i++) {
    		noutpost[i] = (int) Math.min(soil[i]/L, water[i]/W)+1;
    		if (noutpost[i]>king_outpostlist.get(i).size()) {
    			//System.out.printf("After the calculation, the number of outpost for %d king should increase", i);
            	if (i==0) {
            	king_outpostlist.get(i).add(new Pair(0,0));
            	System.out.println("the first player got a new outpost");
            	}
            	if (i==1) {
            		king_outpostlist.get(i).add(new Pair(size-1, 0));
            		System.out.println("the second player got a new outpost");
            	}
            	if (i==2) {
            		king_outpostlist.get(i).add(new Pair(size-1, size-1));
            		System.out.println("the third player got a new outpost");
            	}
            	if (i==3) {
            		king_outpostlist.get(i).add(new Pair(0,size-1));
            		System.out.println("the fourth player got a new outpost");
            	}
    		}
    		
    	}
    	
    }


    void updatemap() {
        for (int i=0; i<size; i++) {
        	for (int j=0; j<size; j++) {
        	updatePoint(grid[i*size+j]);
        	}
        	}
    }
    
    
    static Point PairtoPoint(Pair pr) {
    	return grid[pr.x*size+pr.y];
    }
    static Pair PointtoPair(Point pt) {
    	return new Pair(pt.x, pt.y);
    }

    void validateMove(ArrayList<movePair> nextlist, int id) {
    	
    	//reach = false;
    	for (int i=0; i<nextlist.size(); i++) {
    		movePair mpr = nextlist.get(i);
    	if (mpr.id<king_outpostlist.get(id).size()){
    		Pair current = king_outpostlist.get(id).get(mpr.id);
    		Pair next = mpr.pr;
    		
    		for (int j=0; j<surroundpr(current).size(); j++) {
    			if (surroundpr(current).get(j).equals(next)) {
    				if (!PairtoPoint(next).water) {
    					Pair tmp = new Pair(next.x, next.y);
                		king_outpostlist.get(id).set((mpr.id), tmp);
    				}
    			}
    		}
    		
    	}
    	}
    	
    	Pair target = null;
        if (id ==0) {
        	target = new Pair(0,0);
        }
        if (id == 1) {
        	target = new Pair(size-1,0);
        }
        if (id ==2 ) {
        	target = new Pair(size-1, size-1);
        }
        if (id ==3) {
        	target = new Pair(0,size-1);
        }
        searchlist.clear();
        supplyline(target, id);
        ArrayList<Integer> removeList = new ArrayList<Integer>();
        for (int j=0; j<king_outpostlist.get(id).size(); j++){
        boolean disqual = true;
        for (int i=0; i<searchlist.size(); i++) {
        	if (searchlist.get(i).equals(king_outpostlist.get(id).get(j))) {
        		disqual = false;
        		break;
        	}
        }
        if (disqual) {
        	//for (int i=0; i<searchlist.size(); i++) {
            	System.out.printf("searchlist size is %d", searchlist.size());
            //}
        	System.out.printf("sorry that we have to disqualify %d outpost (%d, %d) of Player %d\n", j, king_outpostlist.get(id).get(j).x, king_outpostlist.get(id).get(j).y, id);
        	removeList.add(j);
        	
        }
        }
        int counter = 0;
        for (int i=0; i<removeList.size(); i++) {
        king_outpostlist.get(id).remove(removeList.get(i)-counter);
        counter = counter+1;
        }
        //System.out.printf("counter is %d\n", counter);
    	
    	if (king_outpostlist.get(id).size()>noutpost[id] && (tick%10==1)) {
    		//System.out.printf("%d 's king_outpostlist size is %d, noutpost size %d", id, king_outpostlist.get(id).size(), noutpost[id]);
    		int del_no = players[id].delete(king_outpostlist, grid);
    		king_outpostlist.get(id).remove(del_no);
    	}
    	
    	
    }

   static boolean hascontained(ArrayList<Pair> list, Pair pr) {
	   for (int j=0; j<list.size(); j++){
		   
           	if (list.get(j).equals(pr)) {
           		return true;
           	}
           }
   		return false;
   }
    
    static void supplyline (Pair target, int id) {
    	//System.out.printf("target is (%d, %d)\n", target.x, target.y);
    	boolean stop = true;
    	int player;
    	player = id;
    	Point source = new Point();
    	ArrayList<Pair> surlist = new ArrayList<Pair>();
    	
    	for (int j=0; j<king_outpostlist.get(id).size(); j++){
    		if (!hascontained(searchlist, king_outpostlist.get(id).get(j))) {
    			stop = false;
    			break;
    		}
            }
    	//if (stop) {
    		//System.out.printf("we found all outposts in the safe area for player %d\n", id);
    	//}
    	if (!stop & !hascontained(searchlist, target)) {
    	//search_depth++;
    	source = grid[target.x*size+target.y];
    	searchlist.add(target);
    	surlist = surround(target);
    	for (int i=0; i<surlist.size(); i++) {
    		if (!PairtoPoint(surlist.get(i)).water && (PairtoPoint(surlist.get(i)).ownerlist.size() ==0 || (PairtoPoint(surlist.get(i)).ownerlist.size()== 1 && PairtoPoint(surlist.get(i)).ownerlist.get(0).x==player))){
    			Pair pt = new Pair(surlist.get(i).x, surlist.get(i).y);
    			boolean has = false;
    			if (!grid[pt.x*size+pt.y].water) {
    	    		for (int j=0; j<searchlist.size(); j++) {
    	    			if (searchlist.get(j).equals(pt)) {
    	    				has = true;
    	    			}
    	    		}
    	    		if (!has) {
        				supplyline(pt, id);
    	    		}
    			}	
    			}
    	}
    	}
    }
    	
    
    
    static ArrayList<Pair> surround(Pair start) {
   // 	System.out.printf("start is (%d, %d)", start.x, start.y);
    	ArrayList<Pair> prlist = new ArrayList<Pair>();
    	for (int i=0; i<4; i++) {
    		Pair tmp0 = new Pair(start);
    		Pair tmp;
    		if (i==0) {
    			if (start.x>0) {
    			tmp = new Pair(tmp0.x-1,tmp0.y);
    			prlist.add(tmp);
    			}
    		}
    		if (i==1) {
    			if (start.x<size-1) {
    			tmp = new Pair(tmp0.x+1,tmp0.y);
    			prlist.add(tmp);
    			}
    		}
    		if (i==2) {
    			if (start.y>0) {
    			tmp = new Pair(tmp0.x, tmp0.y-1);
    			prlist.add(tmp);
    			}
    		}
    		if (i==3) {
    			if (start.y<size-1) {
    			tmp = new Pair(tmp0.x, tmp0.y+1);
    			prlist.add(tmp);
    			}
    		}
    		
    	}
    	
    	return prlist;
    }
    
    static ArrayList<Pair> surroundpr(Pair start) {
    	   // 	System.out.printf("start is (%d, %d)", start.x, start.y);
    	    	ArrayList<Pair> prlist = new ArrayList();
    	    	for (int i=0; i<4; i++) {
    	    		Pair tmp0 = new Pair(start);
    	    		//Pair tmp = new Pair();
    	    		if (i==0) {
    	    			if (start.x>0) {
    	    			Pair tmp = new Pair(start.x-1, start.y);
    	    			prlist.add(tmp);
    	    			}
    	    		}
    	    		if (i==1) {
    	    			if (start.x<size-1) {
    	    				Pair tmp = new Pair(start.x+1, start.y);
        	    			prlist.add(tmp);
    	    			}
    	    		}
    	    		if (i==2) {
    	    			if (start.y>0) {
    	    				Pair tmp = new Pair(start.x, start.y-1);
        	    			prlist.add(tmp);
    	    			}
    	    		}
    	    		if (i==3) {
    	    			if (start.y<size-1 ) {
    	    				Pair tmp = new Pair(start.x, start.y+1);
        	    			prlist.add(tmp);
    	    			}
    	    		}
    	    		
    	    	}
    	    	//for (int j=0; j<prlist.size(); j++) {
    	    		//System.out.printf("surround is (%d, %d)", prlist.get(j).x, prlist.get(j).y);
    	    	//}
    	    	return prlist;
    	    }
    // detect whether the player has achieved the requirement
  /*  boolean endOfGame() {
       // if (!mode) {
            // simple mode
            for (int i = 0; i < nrats; ++i) {
                if (getSide(rats[i]) == 1)
                    return false;
            }
            return true;
        //}
      /*  else {
            // advanced mode
            // all black are in upper side
            for (int i = 0; i < nblacks; ++i) {
                if (getSide(sheeps[i]) == 1)
                    return false;
            }
            // all white are in lower side 
            for (int i = nblacks; i < nsheeps; ++i) {
                if (getSide(sheeps[i]) == 0)
                    return false;
            }
            return true;
        }*/

   // }


    void playStep() {
       tick++;        

        // move the player dogs
       if (tick % 10 == 0 && tick !=0){
       	calculateres();
       	//updatemap();
       }
       
	for (int d=0; d<4; d++) {
        try {
        	ArrayList<movePair> nextlist = new ArrayList<movePair>();
        	nextlist = players[d].move(king_outpostlist, noutpost[d], grid);
        	for (int i=0; i<nextlist.size(); i++) {
        	movePair next = new movePair();
        	next = nextlist.get(i);
        	//next = players[d].move(king_outpostlist, noutpost[d], grid);
        	//System.out.printf("Player %d is moving (%d, %d) to (%d, %d)\n", d, king_outpostlist.get(d).get(next.id).x, king_outpostlist.get(d).get(next.id).y, next.pr.x, next.pr.y);
        	// validate player move
       //     if (validateMove(next, d)) {
        	validateMove(nextlist, d);
            	/*if (next.delete) {
            		king_outpostlist.get(d).remove(next.id);
            	}
            	else {
            		Pair tmp = new Pair(next.pr.x, next.pr.y);
            		king_outpostlist.get(d).set((next.id), tmp);
            	}*/
            	updatemap();
         //   }
           // else {
            //	System.out.println("valid didn't pass...");
            //}
        	}
            
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("[ERROR] Player throws exception!!!!");
                //next[d] = pipers[d]; // let the dog stay
            }

         /*   if (verbose) {
                System.err.format("Piper %d moves from (%.2f,%.2f) to (%.2f,%.2f)\n", 
                                  d+1, pipers[d].x, pipers[d].y, next.pr.x, next.pr.y);
            }
*/
        }
	if (tick == nrounds) {
		calculateres();
		for (int i=0; i<4; i++) {
		System.err.printf("Player %d control water %f, land %f\n", water[i], soil[i]);
		}
	}
    }
   
    void play() {
       /* while (tick <= MAX_TICKS) {
            if (endOfGame()) break;
            playStep();
        }
        
        if (tick > MAX_TICKS) {
            // Time out
            System.err.println("[ERROR] The player is time out!");
        }
        else {
            // Achieve the goal
            System.err.println("[SUCCESS] The player achieves the goal in " + tick + " ticks.");
        }*/
    }

    void init() {
 	   
        for (int i=0; i<size; i++) {
     	   for (int j=0; j<size; j++) {
     		   grid[i*size+j] = new Point(i, j, false);
     	   }
        }
        for (int i=0; i<4; i++) {
        	ArrayList<Pair> tmp_arr = new ArrayList<Pair>();
        	if (i==0)
        	tmp_arr.add(new Pair(0,0));
        	if (i==1)
        		tmp_arr.add(new Pair(size-1, 0));
        	if (i==2)
        		tmp_arr.add(new Pair(size-1, size-1));
        	if (i==3)
        		tmp_arr.add(new Pair(0,size-1));
        	king_outpostlist.add(tmp_arr);
        }
        for (int i=0; i<4; i++) {
    		noutpost[i] = 1;
    	}
        
     }


    Outpost()
    {
       this.players = players;
    }

    public void read(String map) {
    	List<Pair> list = new ArrayList<Pair>();
    	File file = new File(map);
    	BufferedReader reader = null;
    	int counter = 0;
    	try {
    	    reader = new BufferedReader(new FileReader(file));
    	    String text = null;

    	    while ((text = reader.readLine()) != null) {
    	    	java.util.List<String> item2 = new ArrayList();
				item2 = Arrays.asList(text
						.split(" "));
				ArrayList array_tmp0 = new ArrayList();
				Pair pr = new Pair();
				pr.x = Integer.parseInt(item2.get(0));
				pr.y = Integer.parseInt(item2.get(1));
    	    	list.add(pr);
    	    	counter = counter +1;
    	    	grid[pr.x*size+pr.y].water = true;
    	    	grid[(size-pr.y)*size+pr.x].water = true;
    	    	grid[(100-pr.x)*size+(100-pr.y)].water = true;
    	    	grid[pr.y*size+100-pr.x].water = true;
    	    	//grid[(100-pr.x)*size+pr.y].water = true;
    	    	//grid[(100-pr.x)*size+100-pr.y].water = true;
    	    }
    	} catch (FileNotFoundException e) {
    	    e.printStackTrace();
    	} catch (IOException e) {
    	    e.printStackTrace();
    	} finally {
    	    try {
    	        if (reader != null) {
    	            reader.close();
    	        }
    	    } catch (IOException e) {
    	    }
    	}
    	//System.out.println(counter);
    }
	public static void main(String[] args) throws Exception
	{
        // game parameters
        String map = null;
        String group0 = null;
        String group1 = null;
        String group2 = null;
        String group3 = null;
        
        
        if (args.length > 0)
            map = args[0];
        if (args.length > 1)
            r_distance = Integer.parseInt(args[1]);
        if (args.length > 2)
            L = Integer.parseInt(args[2]);
        if (args.length > 3)
            W = Integer.parseInt(args[3]);
        if (args.length > 4)
        	gui = Boolean.parseBoolean(args[4]);
        if (args.length >5) 
        	group0 = args[5];
        if (args.length>6)
        	group1 = args[6];
        if (args.length>7)
        	group2 = args[7];
        if (args.length>8)
        	group3 = args[8];
        if (args.length>9)
        	nrounds = Integer.parseInt(args[9]);
        // load players
        
        players[0] = loadPlayer(group0, 0);
        players[0].init();
        players[1] = loadPlayer(group1, 1);
        players[1].init();
        players[2] = loadPlayer(group2, 2);
        players[2].init();
        players[3] = loadPlayer(group3, 3);
        players[3].init();
        
        // create game
        Outpost game = new Outpost();
        // init game
        game.init();
        game.read(map);
       
        // play game
        //if (gui) {
            game.playgui();
        //}
        //else {
          //  game.play();
        //}

    }        

    int tick = 0;

    static double dimension = 100.0; // dimension of the map
    static Random random = new Random();
}
