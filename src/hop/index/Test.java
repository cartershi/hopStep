package hop.index;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Random;
import java.util.Scanner;


//test the correctness of the code
public class Test {
	String testfile;
	String cmpfile;
	int row = 20; // data size
	int column = 20;
	int nodesize = 100;

	// generate edge data
	void generate() throws IOException {
		File testFile = new File(testfile);
		if (!testFile.exists()) { // if file doesn't exists, then create it
			testFile.createNewFile();
		}
		Random r = new Random();
		PrintWriter fop = new PrintWriter(testFile);
		for (int i = 1; i <= row; i++) {
			for (int j = 1; j <= column; j++)
				fop.println(Math.abs(r.nextInt() % nodesize) + " " + Math.abs(r.nextInt() % nodesize) + " " + 1);
			// System.out.println(i);
		}
		fop.close();
		System.out.println("generate end");
	}

	// test whether two files are the same
	void testFile(String st1, String st2) throws IOException {

		Scanner input1 = new Scanner(new File(st1));
		Scanner input2 = new Scanner(new File(st2));
		int count = 0;
		int i;
		while (input1.hasNextInt() && input2.hasNextInt()) {
			count++;
			if (input1.hasNextInt() != input2.hasNextInt()) {
				System.out.println("sort diff in line" + count + "\n");
				input1.close();
				input2.close();
				return;
			}
			for (i = 1; i <= 3; i++) {
				if (input1.nextInt() != input2.nextInt()) {
					System.out.println("sort diff in line" + count + "\n");
					input1.close();
					input2.close();
					return;
				}
			}
		}
		input1.close();
		input2.close();
		System.out.println("test file no difference!");
	}

	// test whether huge file is sorted
	void testSort() throws IOException {
		File testFile = new File(testfile);
		File cmpFile = new File(testfile + ".sort");
		Scanner input = new Scanner(testFile);
		PrintWriter fop = new PrintWriter(cmpFile);
		Label[] memLabel = new Label[row * column];
		for (int i = 0; i < row * column; i++)
			memLabel[i] = new Label();
		int count = -1;
		while (input.hasNextInt()) {
			count++;
			memLabel[count].x = input.nextInt();
			memLabel[count].y = input.nextInt();
			memLabel[count].w = input.nextInt();
		}
		Arrays.sort(memLabel, 0, count + 1, new LabelCompare());
		for (int i = 0; i <= count; i++)
			fop.println(memLabel[i].x + " " + memLabel[i].y + " " + memLabel[i].w);
		input.close();
		fop.close();
		testFile(testfile, testfile + ".sort");
		System.out.println("test sort end!");
	}

	// test whether relabel is right
	void testRelabel() throws IOException {
		int graph[][];
		graph = new int[nodesize][nodesize];
		int i, j;
		for (i = 0; i < nodesize; i++)
			for (j = 0; j < nodesize; j++)
				graph[i][j] = 0;

		// input
		File testFile = new File(testfile);
		Scanner input = new Scanner(testFile);
		int x, y, w, t;
		while (input.hasNextInt()) {
			x = input.nextInt();
			y = input.nextInt();
			w = input.nextInt();
			if (x == y)
				w = 0;
			if (x > y) {
				t = x;
				x = y;
				y = t;
			}
			if (graph[x][y] == 0)
				graph[x][y] = w;
			if (graph[x][y] > w)
				graph[x][y] = w;
			graph[y][x] = graph[x][y];
		}
		input.close();

		// get new label
		int count = 0;
		int nodeNum = 0;
		Label memLabel[] = new Label[nodesize];
		for (i = 0; i < nodesize; i++) {
			count = 0;
			for (j = 0; j < nodesize; j++) {
				if (graph[i][j] != 0)
					count++;
			}
			if (count != 0)
				memLabel[nodeNum++] = new Label(count, i, 0);
		}
		Arrays.sort(memLabel, 0, nodeNum, new LabelCompare());

		/*
		 * //test node degree count File relabelFile = new File(testfile + ".relabel");
		 * PrintWriter fop = new PrintWriter(relabelFile); for (i = 0; i < nodesize;
		 * i++) fop.println(memLabel[i].x + " " + memLabel[i].y + " " + memLabel[i].w);
		 * fop.close();
		 * 
		 * testFile(cmpfile + ".label.rank", testfile+ ".relabel");
		 */

		int newLabel[] = new int[nodesize];
		for (i = 0; i < nodeNum; i++) {
			newLabel[memLabel[i].y] = i + 1;
		}

		// relabel
		memLabel = new Label[row * column];
		count = -1;
		for (i = 0; i < nodesize; i++)
			for (j = i + 1; j < nodesize; j++)
				if (graph[i][j] != 0) {
					count++;
					x = newLabel[i];
					y = newLabel[j];
					if (x > y)
						memLabel[count] = new Label(y, x, graph[i][j]);
					if (x < y)
						memLabel[count] = new Label(x, y, graph[i][j]);
				}
		Arrays.sort(memLabel, 0, count + 1, new LabelCompare());

		File relabelFile = new File(testfile + ".relabel");
		PrintWriter fop = new PrintWriter(relabelFile);
		for (i = 0; i <= count; i++)
			fop.println(memLabel[i].x + " " + memLabel[i].y + " " + memLabel[i].w);
		fop.close();

		// testFile(cmpfile, testfile+ ".relabel");
		// relabelFile.delete();
		//System.out.println("test relabel end!");

	}

	void testHopStep() throws IOException {
		int graph[][];
		graph = new int[nodesize + 1][nodesize + 1];
		int i, j;
		for (i = 1; i < nodesize + 1; i++)
			for (j = 1; j < nodesize + 1; j++)
				graph[i][j] = 10000000;

		// input
		testRelabel();
		File testFile = new File(testfile + ".relabel");
		Scanner input = new Scanner(testFile);
		int x, y, k, n;
		n = 1;
		while (input.hasNextInt()) {
			x = input.nextInt();
			y = input.nextInt();
			input.nextInt();
			if (x > n)
				n = x;
			if (y > n)
				n = y;
			graph[x][y] = graph[y][x] = 1;
		}
		input.close();
		for (k = 1; k <= n; k++)
			for (i = 1; i <= n; i++)
				for (j = 1; j <= n; j++)
					if (i != j)
						if (graph[i][j] > graph[i][k] + graph[k][j])
							graph[i][j] = graph[i][k] + graph[k][j];

		File relabelFile = new File(testfile + ".floyd");
		PrintWriter fop = new PrintWriter(relabelFile);
		for (i = 1; i <= n; i++) graph[i][i] = 0;
		for (i = 1; i <= n; i++) {
			for (j = 1; j <= n; j++)
				fop.print(graph[i][j] + " ");
			fop.println();
		}
		fop.close();
	}

	void testGraph() throws IOException {
		int x, y, w;
		int Node = -1;
		ArrayList<ArrayList<Label>> graph = new ArrayList<ArrayList<Label>>();

		Scanner input = new Scanner(new File(testfile + ".ans"));
		while (input.hasNext()) {
			x = input.nextInt();
			y = input.nextInt();
			w = input.nextInt();

			/*
			 * Edge edge = it.next(); x = edge.s; y = edge.t;
			 */

			if (x > Node) { // record the max node
				for (int i = Node + 1; i <= x; i++)
					graph.add(new ArrayList<Label>());
				Node = x;
			}
			if (y > Node) {
				for (int i = Node + 1; i <= y; i++)
					graph.add(new ArrayList<Label>());
				Node = y;
			}
			graph.get(x).add(new Label(x, y, w));
			//graph.get(y).add(new Label(y, x, w));
		}
		input.close();
		
		int i ,j;
		File relabelFile = new File(testfile + ".hop");
		PrintWriter fop = new PrintWriter(relabelFile);
		for (i = 1; i <= Node; i++) {
			for (j = 1; j <= Node; j++) {				
				x = 0;
				y = 0;
				w = 0;
				for (int k = 0; k < graph.get(i).size(); k++)
					if (graph.get(i).get(k).y == j) {
					w = graph.get(i).get(k).w;
					break;
				}
				for (int k = 0; k < graph.get(j).size(); k++)
					if (graph.get(j).get(k).y == i) {
						w = graph.get(j).get(k).w;
						break;
					}
				if (i != j) {
				while (x < graph.get(i).size() && y < graph.get(j).size()) {					
					if (graph.get(i).get(x).y == graph.get(j).get(y).y) {
						if (w == 0) w = graph.get(i).get(x).w + graph.get(j).get(y).w;
						else 
							if (w > graph.get(i).get(x).w + graph.get(j).get(y).w) 
								w = graph.get(i).get(x).w + graph.get(j).get(y).w;
						x++;
						y++;
						continue;
					}
					
					if (graph.get(i).get(x).y > graph.get(j).get(y).y) y++;
					else
						if (graph.get(i).get(x).y < graph.get(j).get(y).y) x++;
				}
				}
				fop.print(w + " ");
			}
			fop.println();
		}
		fop.close();
		System.out.println("test graph end!");
		testHopStep();
		testFile(testfile + ".ans", testfile+ ".hop");
	}

	Test(String file) {
		testfile = file;
		cmpfile = file + ".tmp";
	}
}
