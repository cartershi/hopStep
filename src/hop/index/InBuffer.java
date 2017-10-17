package hop.index;

import java.io.*;
import java.util.*;

public class InBuffer  {
	Label[] buffer;	//input buffer
	int bufferLen;	//record the number of Labels in the buffer
	int count;	//count how many of the buffer has been used
	Variable data;	//common const
	Scanner input;	//input Label file
	boolean isEnd;	//record whether input is end
	boolean Started;
	public InBuffer(File InFile) throws IOException{
		isEnd = false;
		data = new Variable();	//get all needed variable
		buffer = new Label[data.BufSize];
		input = new Scanner(InFile);
		Started = false;
	}
	
	//start the Inbuffer
	void start() throws IOException {
		inToBuffer();
		Started = true;
	}
	
	//read file into buffer
	void inToBuffer() throws IOException{
		count = 0;
		bufferLen = 0;
		if (isEnd) return;		
		while (input.hasNextInt() && bufferLen < data.BufSize) {
			if (!Started) buffer[bufferLen] = new Label();
			buffer[bufferLen].x = input.nextInt();
			buffer[bufferLen].y = input.nextInt();
			buffer[bufferLen].w = input.nextInt();
			bufferLen++;
		}
		if (bufferLen == 0) {
			isEnd = true;	//empty buffer means the input is empty
			input.close();
		}
	}
	
	boolean isend() throws IOException {
		if (!Started) start();
		return isEnd;
	}
	void farewell() {
		if (!isEnd) input.close();
	}
	
	//get next Label 
	Label topLabel() throws IOException{
		if (!Started) start();
		if (isEnd) return null;
		
		return  buffer[count];
	}
	//pop the next label
	Label nextLabel() throws IOException{
		if (!Started) start();
		Label ret = new Label();
		if (isEnd) return null;
		
		//now the buffer must be nonmepty
		ret.x = buffer[count].x;
		ret.y = buffer[count].y;
		ret.w = buffer[count].w;
		count++;
		if (count >= bufferLen) {
			inToBuffer();
		}
		return ret;
	}
}