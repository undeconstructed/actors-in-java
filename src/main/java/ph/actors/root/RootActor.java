package ph.actors.root;

import ph.actors.actors.Actor;
import ph.actors.foo.Foo;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * TODO
 */
public class RootActor extends Actor<Root> implements Root {

	@Override
	public ListenableFuture<Boolean> shove() {
		try {
			Foo foo100 = find(Foo.class, 100);
			String r = foo100.doThing().get();
			System.out.println("got " + r);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return Futures.immediateFuture(true);
	}
}
