package hop.index;

public class Label {
	int x, y, w;
	Label(){
		x = y = w = 0;
	}
	Label(int x, int y, int w) {
		this.x = x;
		this.y = y;
		this.w = w;
	}
	Label(Label u) {
		this.x = u.x;
		this.y = u.y;
		this.w = u.w;
	}
	boolean compareSmall(Label u){
		return (x < u.x)|(x == u.x && y < u.y)
				|(x == u.x && y == u.y && w < u.w);
	}
	void exchange(Label u) {
		int s;
		s = x; x = u.x; u.x = s;
		s = y; y = u.y; u.y = s;
		s = w; w = u.w; u.w = s;
	}
	void assign(Label u) {
		x = u.x;
		y = u.y;
		w = u.w;
	}
}
