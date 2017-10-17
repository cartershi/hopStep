package hop.index;

public class Variable {
	long MemSize;
	int BufSize;
	int BPMSize;
	boolean debug = false;

	public Variable() {
		if (!debug) {
			MemSize = 64 * 1024 * 1024; // 64M
			BufSize = 1 * 1024 * 1024; // 1M
		} else {
			MemSize = 64 * 1024; //
			BufSize = 1024;
		}
		BPMSize = (int) MemSize / BufSize;
	}
}
