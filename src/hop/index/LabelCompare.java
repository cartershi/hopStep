package hop.index;

import java.util.Comparator;

public class LabelCompare implements Comparator<Label> {
	public int compare(Label s1, Label s2) {
		int result = s1.x > s2.x ? 1 : (s1.x == s2.x ? 0 : -1);
		if (result == 0)
			result = (s1.y > s2.y) ? 1 : (s1.y == s2.y ? 0 : -1);
		if (result == 0)
			result = (s1.w > s2.w) ? 1 : (s1.w == s2.w ? 0 : -1);
		return result;
	}
}
