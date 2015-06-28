package ph.actors.actors;

import ph.actors.bar.BarActor;
import ph.actors.foo.FooActor;
import ph.actors.root.RootActor;

/**
 * TODO
 */
public class Main {

	public static void main(String[] args) throws Exception {
		Stage stage = new Stage();
		stage.addRole(FooActor.class);
		stage.addRole(BarActor.class);
		stage.start(stage.addRole(RootActor.class));
	}
}
