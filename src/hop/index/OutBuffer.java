package hop.index;

import java.io.*;

public class OutBuffer {
	Label[] buffer;	//input buffer
	int bufferLen;	//record the number of Labels in the buffer
	int count;	//count how many of the buffer has been used
	Variable data;	//common const
	PrintWriter fop;
    File file;
	OutBuffer(File file)throws IOException{
        fop = new PrintWriter(file);
        count = -1;
        data = new Variable();	//get all needed variable
		buffer = new Label[data.BufSize];
		for (int i = 0; i < data.BufSize; i++) buffer[i] = new Label();
	}
	
	//insert an label into the file 
	void insert(Label u) {
		if (count >= data.BufSize - 1) flush();
		count++;
		buffer[count].x = u.x;
		buffer[count].y = u.y;
		buffer[count].w = u.w;
	}
	
	//say goodbye the outBuffer
	void farewell() {
		flush();
		fop.close();
	}
	
	//write the buffer into the file
	void flush() {
		for (int i = 0; i <= count; i++)
			fop.println(buffer[i].x + " " + buffer[i].y + " " + buffer[i].w);
		count = -1;
	}
}
