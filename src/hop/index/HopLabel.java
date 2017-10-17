package hop.index;

import java.io.*;
import java.nio.channels.FileChannel;
import java.util.*;

public class HopLabel {
	Variable data; // common const

	HopLabel() {
		data = new Variable();
	}

	boolean debugxsort = false;
	// sort labels in file
	void xsort(String sourceName) throws IOException {
		String SortFile = sourceName + ".sort";
		File SourceFile = new File(sourceName);
		if (SourceFile.length() == 0) return;
		File BinFile = new File(SortFile);
		if (!BinFile.exists()) { // if file doesn't exists, then create it
			BinFile.createNewFile();
		}

		// init memlabel and buffer
		Label[] memLabel = new Label[(int) data.MemSize];

		InBuffer inBuf = new InBuffer(SourceFile);
		OutBuffer outBuf = new OutBuffer(BinFile);

		long labelNum = 0;
		// sort all batch
		while (!inBuf.isend()) {
			int count = -1;
			Label retLabel = new Label();
			while (!inBuf.isend() && count < data.MemSize - 1) { // read until the end or memory empty
				retLabel = inBuf.nextLabel();
				count++;
				memLabel[count] = new Label();	//we don't new a Label until we need it
				memLabel[count].assign(retLabel);
			}
			labelNum = labelNum + count + 1;
			Arrays.sort(memLabel, 0, count + 1, new LabelCompare());
			for (int i = 0; i <= count; i++)
				outBuf.insert(memLabel[i]);
		}
		outBuf.farewell();

		if (debugxsort)
			System.out.println("batch sort end");

		// if labels are less than memory can afford, we are done
		if (labelNum <= data.MemSize) {
			if (SourceFile.getName().length() < BinFile.getName().length()) {
				SourceFile.delete();
				BinFile.renameTo(new File(sourceName)); // we need binFile, while it name is wrong, so rename it
			} else
				SourceFile.delete();
			return;
		}

		// if labels are way too much, we use merge sort to sort merge all sorted
		// batches

		int heap[][], heapSize, cntLocation[]; // an heap to merge
		long posFile[];
		heap = new int[data.BPMSize + 1][2]; // the size of aheap is memory can afford
		cntLocation = new int[data.BPMSize];
		posFile = new long[data.BPMSize];

		for (long len = data.MemSize; len < labelNum; len *= data.BPMSize) {
			// now we generate answers from SourceFile to BinFile
			// first exchange SourceFile and BinFile, now that each iteration results are
			// exported to BinFile
			if (SourceFile.getName().length() > BinFile.getName().length()) {
				SourceFile = new File(sourceName);
				BinFile = new File(SortFile);
			} else {
				SourceFile = new File(SortFile);
				BinFile = new File(sourceName);
			}
			InBuffer[] newinBuf = new InBuffer[data.BPMSize];
			OutBuffer newoutBuf = new OutBuffer(BinFile);
			int count;

			// lastpos record the place it should has sorted in batch
			for (long lastPos = 0; lastPos < labelNum; lastPos += len * (data.BPMSize)) {

				heapSize = 0;
				count = 0;

				for (int i = 0; i < data.BPMSize; i++)
					newinBuf[i] = new InBuffer(SourceFile);

				// read the i-th batch in the memory
				for (int i = 0; i < data.BPMSize && lastPos + i * len < labelNum; i++) {

					// jump the inbuf to where it should read
					for (long j = 1; j <= lastPos + i * len; j++)
						newinBuf[i].input.nextLine();

					Label retLabel = new Label();
					posFile[i] = 0; // where the i-th readBuf reach
					heapSize = i + 1;
					heap[heapSize][0] = count; // the location of the batch in memory
					heap[heapSize][1] = i; // the number of batch
					while (!newinBuf[i].isend() && count - heap[heapSize][0] < data.BufSize) { // each batch read
																								// edgePerBuf
						retLabel = newinBuf[i].nextLabel();
						memLabel[count].assign(retLabel);
						count++;
					}
					cntLocation[i] = count;
					for (int j = heapSize, jj; j > 1; j = jj) { // a min heap
						jj = (j >> 1);
						if (memLabel[heap[j][0]].compareSmall(memLabel[heap[jj][0]])) {
							int x = heap[j][0];
							heap[j][0] = heap[jj][0];
							heap[jj][0] = x;
							x = heap[j][1];
							heap[j][1] = heap[jj][1];
							heap[jj][1] = x;
						} else
							break;
					}
				}

				while (heapSize > 0) // while the heap is not empty
				{
					int minEdge = heap[1][0], pi = heap[1][1];
					newoutBuf.insert(memLabel[minEdge]);

					if (minEdge < cntLocation[pi] - 1 || posFile[pi] < (len - 1) / data.BufSize) {
						if (minEdge < cntLocation[pi] - 1) // a buffer is not end
							heap[1][0] = minEdge + 1;
						else { // the batch is not end while a buffer ends
							posFile[pi]++;
							cntLocation[pi] = heap[1][0] = pi * data.BufSize; // get start location
							Label retLabel = new Label();
							while (!newinBuf[pi].isend() && cntLocation[pi] - heap[1][0] < data.BufSize
									&& data.BufSize * posFile[pi] + cntLocation[pi] - heap[1][0] < len) {
								retLabel = newinBuf[pi].nextLabel();
								memLabel[cntLocation[pi]].assign(retLabel);
								cntLocation[pi]++;
							}

							if (newinBuf[pi].isend() || data.BufSize * posFile[pi] + cntLocation[pi] - heap[1][0] >= len)
								posFile[pi] = (len - 1) / data.BufSize;// the batch has been read into the buffer

						}
					} else { // the batch is end
						heap[1][0] = heap[heapSize][0];
						heap[1][1] = heap[heapSize][1];
						heapSize--;
					}

					// modify the min heap
					for (int j = 1, jj; (j << 1) <= heapSize; j = jj) {
						jj = (j << 1);
						if (jj < heapSize && memLabel[heap[jj + 1][0]].compareSmall(memLabel[heap[jj][0]]))
							jj++;
						if (memLabel[heap[jj][0]].compareSmall(memLabel[heap[j][0]])) {
							int x = heap[j][0];
							heap[j][0] = heap[jj][0];
							heap[jj][0] = x;
							x = heap[j][1];
							heap[j][1] = heap[jj][1];
							heap[jj][1] = x;
						} else
							break;
					}
				}
				// make sure all ios are end
				for (int i = 0; i < data.BPMSize; i++)
					newinBuf[i].farewell();
			}
			newoutBuf.farewell();
		}
		// remember binFile is the goal file and sourcefile is not needed
		if (SourceFile.getName().length() < BinFile.getName().length()) {
			boolean filedelete = SourceFile.delete();
			if (!filedelete)
				System.out.println("file delete failed");
			BinFile.renameTo(new File(sourceName)); // we need binFile, while it name is wrong, so rename it
		} else
			SourceFile.delete();

		if (debugxsort)
			System.out.println("xsort end\n");
	}

	
	// copy file from source to goal
	void copyfile(String sourceName, String goalName) throws IOException {
		File SourceFile = new File(sourceName);
		File GoalFile = new File(goalName);
		FileChannel inputChannel = null;
		FileChannel outputChannel = null;
		try {
			FileInputStream FileIn = new FileInputStream(SourceFile);
			inputChannel = FileIn.getChannel();
			FileOutputStream FileOut = new FileOutputStream(GoalFile);
			outputChannel = FileOut.getChannel();
			outputChannel.transferFrom(inputChannel, 0, inputChannel.size());
			FileIn.close();
			FileOut.close();
		} finally {
			inputChannel.close();
			outputChannel.close();
		}
	}

	
	// make sure label.x < label.y
	void vaildateLabel(String sourceName) throws IOException {
		File SourceFile = new File(sourceName);
		File BinFile = new File(sourceName + ".vaildate");
		if (!BinFile.exists()) { // if file doesn't exists, then create it
			BinFile.createNewFile();
		}
		InBuffer inBuf = new InBuffer(SourceFile);
		OutBuffer outBuf = new OutBuffer(BinFile);
		Label retLabel;
		int t;
		while (!inBuf.isend()) {
			retLabel = inBuf.nextLabel();
			if (retLabel.x != retLabel.y) {
				if (retLabel.x > retLabel.y) {
					t = retLabel.x;
					retLabel.x = retLabel.y;
					retLabel.y = t;
				}
				outBuf.insert(retLabel);
			}
		}
		outBuf.farewell();
		inBuf.farewell();
		SourceFile.delete();
		BinFile.renameTo(new File(sourceName));
	}

	
	// delete multiple labels while the file is sorted
	void deleteSortedMultipleLabel(String sourceName) throws IOException {
		File SourceFile = new File(sourceName);
		File BinFile = new File(sourceName + ".delete");
		if (!BinFile.exists()) { // if file doesn't exists, then create it
			BinFile.createNewFile();
		}
		InBuffer inBuf = new InBuffer(SourceFile);
		OutBuffer outBuf = new OutBuffer(BinFile);
		
		Label retLabel;	//get next label
		Label prevLabel;	//record lastest resverved label
		prevLabel = new Label();
		prevLabel.x = -1;
		prevLabel.y = -1;
		while (!inBuf.isend()) {
			retLabel = inBuf.nextLabel();
			
			//multiple label with bigger w will be deleted
			if (prevLabel.x == retLabel.x &&prevLabel.y == retLabel.y
					&&prevLabel.w <= retLabel.w) continue;
			outBuf.insert(retLabel);
			prevLabel.assign(retLabel);			
		}
		outBuf.farewell();
		inBuf.farewell();
		SourceFile.delete();
		BinFile.renameTo(new File(sourceName));
	}

	
	//swap label.x laebl.y
	void swapLabelXY(String sourceName) throws IOException {
		File SourceFile = new File(sourceName);
		File BinFile = new File(sourceName + ".swap");
		if (!BinFile.exists()) { // if file doesn't exists, then create it
			BinFile.createNewFile();
		}
		InBuffer inBuf = new InBuffer(SourceFile);
		OutBuffer outBuf = new OutBuffer(BinFile);
		
		Label retLabel;
		int t;
		while (!inBuf.isend()) {
			retLabel = inBuf.nextLabel();
			t = retLabel.x;
			retLabel.x = retLabel.y;
			retLabel.y = t;
			outBuf.insert(retLabel);
		}
		outBuf.farewell();
		inBuf.farewell();
		SourceFile.delete();
		BinFile.renameTo(SourceFile);
	}
	
	
	//reLabel nodes with regard to their vertex degrees
	//assume labels in the file are sorted
	void reLabel(String sourceName) throws IOException {
		String LabelName = sourceName + ".label";
		String TmpName = LabelName + ".tmp";
		File SourceFile = new File(sourceName);
		File TmpFile = new File(TmpName);
		if (!TmpFile.exists()) { // if file doesn't exists, then create it
			TmpFile.createNewFile();
		}
		
		//get invaild edges to get node degrees
		copyfile(sourceName,TmpName);
		swapLabelXY(TmpName);
		xsort(TmpName);
		
		File LabelFile = new File(LabelName);	//relabel information to LabelFile
		if (!LabelFile.exists()) { // if file doesn't exists, then create it
			LabelFile.createNewFile();
		}
				
		//now we compute node degree
		InBuffer inVaildBuf = new InBuffer(SourceFile);
		InBuffer inInvaildBuf = new InBuffer(TmpFile);
		OutBuffer outBuf = new OutBuffer(LabelFile);
		int labelid = -1;
		Label degreeLabel = new Label();
		while(!inVaildBuf.isend() || !inInvaildBuf.isend()) {	//all labels must be used
			labelid++;
			int count = 0;	//count the degree of each node
			while(!inVaildBuf.isend() && labelid == inVaildBuf.topLabel().x) {
				inVaildBuf.nextLabel();
				count++;
			}
			while(!inInvaildBuf.isend() && labelid == inInvaildBuf.topLabel().x) {
				inInvaildBuf.nextLabel();
				count++;
			}
			if (count != 0) {	//single vertex will not be recorded 
				degreeLabel.x = labelid;	//record the id and it's degree
				degreeLabel.y = count;
				degreeLabel.w = 0;
				outBuf.insert(degreeLabel);
			}
		}
		outBuf.farewell();
		inVaildBuf.farewell();
		inInvaildBuf.farewell();
		TmpFile.delete();
		
		//now sort labels according to their degrees	
		File TmpRankFile = new File(LabelName + ".rank");
		if (!TmpRankFile.exists()) { // if file doesn't exists, then create it
			TmpRankFile.createNewFile();
		}
		
		//sort labels
		copyfile(LabelName,LabelName + ".rank");
		swapLabelXY(LabelName + ".rank");
		xsort(LabelName + ".rank");

		
		InBuffer inRankBuf = new InBuffer(TmpRankFile);
		outBuf = new OutBuffer(LabelFile);
		Label retLabel;
		int count = 0;
		while (!inRankBuf.isend()) {
			count++;
			retLabel = inRankBuf.nextLabel();			
			retLabel.x = retLabel.y;	//previous id
			retLabel.y = count;	//now id
			outBuf.insert(retLabel);
		}
		outBuf.farewell();
		inRankBuf.farewell();
		xsort(LabelName);
		TmpRankFile.delete();
		
		
		//relabel source file
		
		//firstly relabel x
		InBuffer inLabelBuf = new InBuffer(SourceFile);	//labels need to be relabeled
		InBuffer inRelabelBuf = new InBuffer(LabelFile);	//relabel  information
		File TmpRelabelFile = new File(sourceName + ".relabel");
		if (!TmpRelabelFile.exists()) { // if file doesn't exists, then create it
			TmpRelabelFile.createNewFile();
		}
		outBuf = new OutBuffer(TmpRelabelFile);
		Label relabelLabel;
		while (!inRelabelBuf.isend() || !inLabelBuf.isend()) {
			retLabel = inRelabelBuf.nextLabel();
			while (!inLabelBuf.isend() &&inLabelBuf.topLabel().x == retLabel.x) {	
				relabelLabel = inLabelBuf.nextLabel();
				relabelLabel.x =  retLabel.y;
				outBuf.insert(relabelLabel);
			}
		}
		outBuf.farewell();
		inLabelBuf.farewell();
		inRelabelBuf.farewell();
		
		//secondly relabel y
		swapLabelXY(sourceName + ".relabel");
		xsort(sourceName + ".relabel");	//now label y are sorted 
		inLabelBuf = new InBuffer(TmpRelabelFile);	//labels need to be relabeled
		inRelabelBuf = new InBuffer(LabelFile);	//relabel  information
		outBuf = new OutBuffer(SourceFile);
		while (!inRelabelBuf.isend() || !inLabelBuf.isend()) {
			retLabel = inRelabelBuf.nextLabel();
			if (retLabel == null) continue;
			while (!inLabelBuf.isend() &&inLabelBuf.topLabel().x == retLabel.x) {	
				relabelLabel = inLabelBuf.nextLabel();
				relabelLabel.x =  relabelLabel.y;	//in fact we also swap relabelLabel.x relabelLabel.y here
				relabelLabel.y =  retLabel.y;
				outBuf.insert(relabelLabel);
			}
		}
		outBuf.farewell();
		inLabelBuf.farewell();
		inRelabelBuf.farewell();
		TmpRelabelFile.delete();
		
		vaildateLabel(sourceName);
		xsort(sourceName);
		
	}
	
	//merge two sorted label files to the first file
	void mergeSortedLabel(String firstName, String secondName) throws IOException {
		File firstFile = new File(firstName);
		File secondFile = new File(secondName);
		File mergeFile = new File(firstName + ".merge");
		if (secondFile.length() == 0) return;
		InBuffer firstInBuffer = new InBuffer(firstFile);
		InBuffer secondInBuffer = new InBuffer(new File(secondName));
		OutBuffer outBuf = new OutBuffer(mergeFile); 
		while (!firstInBuffer.isend() || !secondInBuffer.isend()) {
			if (firstInBuffer.isend()) {	//if first file is empty, then second file has something
				outBuf.insert(secondInBuffer.nextLabel());
				continue;
			}
			if (secondInBuffer.isend()) {	//if second file is empty, then first file has something
				outBuf.insert(firstInBuffer.nextLabel());		
				continue;	
			}
			//or we need smaller label
			if (firstInBuffer.topLabel().compareSmall(secondInBuffer.topLabel()))
				outBuf.insert(firstInBuffer.nextLabel());
			else
				outBuf.insert(secondInBuffer.nextLabel());
		}
		outBuf.farewell();
		firstInBuffer.farewell();
		secondInBuffer.farewell();
		firstFile.delete();
		mergeFile.renameTo(firstFile);
	}
	
	//generate new labels using all edges and previous labels, and store it in newfile
	void generation(String counterName, String prevName, String newName) throws IOException {
		File counterFile = new File(counterName);
		File prevFile = new File(prevName);
		File newFile = new File(newName);
		if (!newFile.exists()) {
			newFile.createNewFile();
		}
		InBuffer prevInBuf = new InBuffer(prevFile);
		InBuffer counterInBuf = new InBuffer(counterFile);
		OutBuffer outBuf = new OutBuffer(newFile);
		Label retLabel, edgeLabel, newLabel;
		newLabel = new Label();
		ArrayList<Label> u = new ArrayList<Label>();
		Label previous=  new Label();
		int t;
		while (!prevInBuf.isend()) {
			retLabel = prevInBuf.nextLabel(); //a new u
			
			if (previous.x != retLabel.x) {	//a new u
				
				u.clear();	//stored u must be updated
				while (!counterInBuf.isend() && counterInBuf.topLabel().x != retLabel.x)	//find u-*
					counterInBuf.nextLabel();
				while (!counterInBuf.isend() && counterInBuf.topLabel().x == retLabel.x)	//store u-*
					u.add(counterInBuf.nextLabel());
				
			}
			
			for (int loc = 0; loc < u.size(); loc++){
				edgeLabel = u.get(loc);
				newLabel.x = retLabel.y;
				newLabel.y = edgeLabel.y;
				newLabel.w = retLabel.w + 1;
				if (newLabel.x == newLabel.y) continue;	//delete unnecessary labels
				if (newLabel.x > newLabel.y) {	//vaildate new labels
					t = newLabel.x;
					newLabel.x = newLabel.y;
					newLabel.y = t;
				}
				outBuf.insert(newLabel);
			}
			
			previous.assign(retLabel);
		}
		outBuf.farewell();
		prevInBuf.farewell();
		counterInBuf.farewell();
	}
	
	//prune labels
	/*
	 * to prune labels u-* in new-label file, we load all u labels in all-labels files in momery(for speeding up), and scan all other labels(outer) 
	 * for each u-v, because u-w are in memory, we only need to scan to find v-w		 
	 * because u-w are sorted, v-w can be scanned in order(inner)
	 * again, because u-v are sorted, v-* can be scanned in order  
	 */
	
	 void prune(String newName, String allName) throws IOException {	 
		
		File newFile = new File(newName);
		File allFile = new File(allName);
		String pruneName = newName + ".prune";
		File pruneFile = new File (pruneName);
		InBuffer newInBuf = new InBuffer(newFile);
		InBuffer allInBufOuter = new InBuffer(allFile);	//outer scan to find u-*
		InBuffer allInBufInner = new InBuffer(allFile);	//inner scan to find v-*
		OutBuffer outBuf = new OutBuffer(pruneFile);
		Label pruneLabel;	//Label tries to prune
		Label previous=  new Label();	//previous label trying to prune
		Label retLabel;
		ArrayList<Label> u = new ArrayList<Label>();
		boolean uncovered;
		int count = 0;
		while (!newInBuf.isend()) {
			System.out.println((count++) + "label deal");
			pruneLabel = newInBuf.nextLabel();	//u-v
			uncovered = true;
			
			if (previous.x != pruneLabel.x) {	//a new u
				allInBufInner.farewell();	//a new u means a new inner scan
				allInBufInner = new InBuffer(allFile);
				
				u.clear();	//stored u must be updated
				while (!allInBufOuter.isend() && allInBufOuter.topLabel().x != pruneLabel.x)	//find u-*
					allInBufOuter.nextLabel();
				while (!allInBufOuter.isend() && allInBufOuter.topLabel().x == pruneLabel.x)	//store u-*(for speed up)
					u.add(new Label(allInBufOuter.nextLabel()));
				
			}
			
			while (!allInBufInner.isend() && allInBufInner.topLabel().x != pruneLabel.y)	//find v-*
				allInBufInner.nextLabel();
			int loc = 0;	//location where of u-* to scan
			while (loc < u.size() && u.get(loc).y < pruneLabel.y)	//try to find u-v
				loc++;
			if (loc == u.size()) continue;	//can't find
			if (u.get(loc).y == pruneLabel.y) uncovered = false;
			while (!allInBufInner.isend() && allInBufInner.topLabel().x == pruneLabel.y) {	//try to prune with v-w
				retLabel = allInBufInner.nextLabel();	//v-w
				while (loc < u.size() && u.get(loc).y < retLabel.y)	//try to find u-w
					loc++;
				if (loc == u.size()) continue;				
				if (u.get(loc).y == retLabel.y && u.get(loc).w + retLabel.w <= pruneLabel.w)	//find u-w & v-w can prune new labels
					uncovered = false;
			}
			if (uncovered) outBuf.insert(pruneLabel);	//the label hasn't been covered
			previous.assign(pruneLabel);
		}
		outBuf.farewell();
		newInBuf.farewell();
		allInBufOuter.farewell();
		newFile.delete();
		pruneFile.renameTo(newFile);
	}
	

	//new prune labels
	void newPrune(String newName, String allName) throws IOException {
		File newFile = new File(newName);
		File allFile = new File(allName);
		String pruneName = newName + ".prune";
		File pruneFile = new File (pruneName);
		InBuffer newInBuf = new InBuffer(newFile);
		InBuffer allInBufOuter = new InBuffer(allFile);	//outer scan to find u-*
		InBuffer allInBufInner = new InBuffer(allFile);	//scan to find all labels 
		OutBuffer outBuf = new OutBuffer(pruneFile);
		Label pruneLabel;	//Label tries to prune
		Label previous=  new Label();	//previous label trying to prune
		Label retLabel;
		ArrayList<Label> u = new ArrayList<Label>();
		
		boolean uncovered;
		Label[] memLabel = new Label[(int) data.MemSize];
		int labelSize = -1;
		int count = 0;
		
		while (!allInBufInner.isEnd && labelSize < data.MemSize - 1) {
			labelSize++;					
			memLabel[labelSize] = new Label(allInBufInner.nextLabel());						
		}
		
		while (!newInBuf.isend()) {
			System.out.println((count++) + " label deal" +  labelSize);
			pruneLabel = newInBuf.nextLabel();	//u-v
			uncovered = true;
			
			if (previous.x != pruneLabel.x) {	//a new u
				
				u.clear();	//stored u must be updated
				while (!allInBufOuter.isend() && allInBufOuter.topLabel().x != pruneLabel.x)	//find u-*
					allInBufOuter.nextLabel();
				while (!allInBufOuter.isend() && allInBufOuter.topLabel().x == pruneLabel.x)	//store u-*(for speed up)
					u.add(new Label(allInBufOuter.nextLabel()));
				
				if (pruneLabel.y < memLabel[0].x) {	//v is less than memLabel
					allInBufInner.farewell();	//a new u means a new inner scan
					allInBufInner = new InBuffer(allFile);
					labelSize = -1;
					while (!allInBufInner.isEnd && labelSize < data.MemSize - 1) {
						labelSize++;					
						memLabel[labelSize] = new Label(allInBufInner.nextLabel());						
					}
				}							
			}
			if (pruneLabel.y > memLabel[labelSize].x && !allInBufInner.isEnd) {	//v is bigger than memLabel 
				labelSize = -1;
				while (!allInBufInner.isEnd && labelSize < data.MemSize - 1) {
					labelSize++;					
					memLabel[labelSize] = new Label(allInBufInner.nextLabel());						
				}
			}
			
			int loc = 0;
			int memLoc = 0;
			int left =0;
			int right = labelSize;
			for (int i = 0; i < u.size(); i++) {
				if (u.get(i).y == pruneLabel.y) {
					uncovered = false;	
					break;
				}
			}
			while (left < right) {	//binary search
				if (memLabel[(left + right) / 2].x < pruneLabel.y) 
					left = (left + right) / 2 + 1;
				else
					right = (left + right) / 2;
			}
			memLoc = left;
			while (uncovered && loc < u.size() && memLoc < labelSize && memLabel[memLoc].x == pruneLabel.y) {
				if (memLabel[memLoc].y == u.get(loc).y) {
					if (memLabel[memLoc].w + u.get(loc).w <= pruneLabel.w) {
						uncovered = false;	
						break;
					}
					else {
						memLoc++;
						loc++;
					}
					continue;
				}
				if (memLabel[memLoc].y > u.get(loc).y) {
					loc++;
					continue;
				}
				if (memLabel[memLoc].y < u.get(loc).y) {
					memLoc++;
					continue;
				}
			}
			
			if (uncovered && !allInBufInner.isEnd && allInBufInner.topLabel().x == pruneLabel.y) {	//v-w have some siblings in disk
				labelSize = -1;
				while (!allInBufInner.isEnd && labelSize < data.MemSize - 1) {
					labelSize++;					
					memLabel[labelSize] = new Label(allInBufInner.nextLabel());						
				}
				memLoc = 0;
				while (loc < u.size() && memLoc < labelSize && memLabel[memLoc].x == pruneLabel.y) {
					if (memLabel[memLoc].y == u.get(loc).y) {
						if (memLabel[memLoc].w + u.get(loc).w <= pruneLabel.w) {
							uncovered = false;					
						}
						else {
							memLoc++;
							loc++;
						}
						continue;
					}
					if (memLabel[memLoc].y > u.get(loc).y) {
						loc++;
						continue;
					}
					if (memLabel[memLoc].y < u.get(loc).y) {
						memLoc++;
						continue;
					}
				}
			}
			
			if (uncovered) outBuf.insert(pruneLabel);	//the label hasn't been covered
			previous.assign(pruneLabel);
		}
		
		outBuf.farewell();
		newInBuf.farewell();
		allInBufOuter.farewell();
		newFile.delete();
		pruneFile.renameTo(newFile);
	}
	
	//hop step label generation
	void  hopStep(String sourceName) throws IOException {
		String counterName = sourceName + ".counter";		
		//File sourceFile = new File(sourceName);
		File counterFile = new File(counterName);
		if (!counterFile.exists()) { // if file doesn't exists, then create it
			counterFile.createNewFile();
		}
		
		//get counter edges for generation
		copyfile(sourceName,counterName);
		swapLabelXY(counterName);
		xsort(counterName);
		
		mergeSortedLabel(counterName, sourceName);	//merge vaild and invaild edges for label generation

		String allName = sourceName.substring(0,sourceName.length() - 4) + ".ans";
		String newName = sourceName + ".new";
		String prevName = sourceName + ".prev";
		File allFile = new File(allName);	//store all labels 
		File newFile = new File(newName);	//store new labels
		File prevFile = new File(prevName);	//store previous labels
		if (!allFile.exists()) { // if file doesn't exists, then create it
			allFile.createNewFile();
		}
		if (!newFile.exists()) { // if file doesn't exists, then create it
			newFile.createNewFile();
		}
		if (!prevFile.exists()) { // if file doesn't exists, then create it
			prevFile.createNewFile();
		}
		copyfile(sourceName, allName);	//all edges are labels
		copyfile(sourceName, prevName);	//all edges are previous labels at first
		int iteration = 0;	//count iteration times
		while (prevFile.length() != 0) {
			generation(counterName, prevName, newName);
			xsort(newName);
			deleteSortedMultipleLabel(newName);
			//prune(newName, allName);	//prune labels
			newPrune(newName, allName);
			copyfile(newName, prevName);
			mergeSortedLabel(allName, newName);
			iteration++;
			System.out.println("iteration" + iteration+ "ends");
		}
		newFile.delete();
		prevFile.delete();
		//counterFile.delete();
	}
	
	static String testst = "E:\\test.txt";
	static String testtmp = "E:\\test.txt.tmp";
	static String testst1 = "E:\\test1.txt";

	// test the correctness of previous code
	public static void main(String[] args) throws IOException {
		Test t1 = new Test(testst);
		t1.generate();
		HopLabel h1 = new HopLabel();
		h1.copyfile(testst, testtmp);
		
		long startTime=System.currentTimeMillis();
		
		//delete Multiple Labels
		h1.vaildateLabel(testtmp);	//make labels vaild
		h1.xsort(testtmp);	//sort all labels
		h1.deleteSortedMultipleLabel(testtmp);

		long endTime=System.currentTimeMillis();
		System.out.println("delete multiple labels costs " + (endTime-startTime) + "ms");
		
		startTime=System.currentTimeMillis();
		
		h1.reLabel(testtmp);
		
		endTime=System.currentTimeMillis();
		
		System.out.println("relabel costs " + (endTime-startTime) + "ms");	

		h1.hopStep(testtmp);
		startTime=System.currentTimeMillis();
		
		t1.testGraph();
		//t1.testHopStep();
		//t1.testRelabel();
		//t1.testFile();
		// t1.testSort();
		
		endTime=System.currentTimeMillis();
		
		System.out.println("test relabel costs " + (endTime-startTime) + "ms");		
	}
}
