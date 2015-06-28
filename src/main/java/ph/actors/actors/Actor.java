package ph.actors.actors;

/**
 * TODO
 */
public class Actor<Iface> {

	private Key key;
	private Stage stage;

	final void setup(Key key, Stage stage) {
		this.key = key;
		this.stage = stage;
	}

	protected Key self() {
		return key;
	}

	protected <I> I find(Class<I> i, long id) {
		return stage.find(key, i, id);
	}
}
