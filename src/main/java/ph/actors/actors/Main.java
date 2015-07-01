package ph.actors.actors;

import ph.actors.bar.BarActor;
import ph.actors.foo.FooActor;
import ph.actors.root.RootActor;

/**
 * TODO
 */
public class Main {

	public static void main(String[] args) throws Exception {
		Platform stage = new Platform();
		stage.addActorType(FooActor.class);
		stage.addActorType(BarActor.class);
		stage.start(stage.addActorType(RootActor.class));
	}
}
