package ph.actors.root;

import java.util.concurrent.CompletionStage;

import ph.actors.actors.Actor;
import ph.actors.foo.Foo;

/**
 * TODO
 */
public class RootActor extends Actor<Root> implements Root {

	@Override
	public CompletionStage<Boolean> start() {
		CompletionStage<Boolean> fg = find(Foo.class, 100).doThing(10).thenApply(r -> {
			System.out.println("got " + r);
			return true;
		});
		fg.thenRunAsync(() -> {
			try {
				Thread.sleep(5000);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			System.out.println("sshhhh...");
		});
		return fg;
	}
}
