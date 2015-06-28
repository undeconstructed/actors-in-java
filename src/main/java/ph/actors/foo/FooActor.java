package ph.actors.foo;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import ph.actors.actors.Actor;

/**
 * TODO
 */
public class FooActor extends Actor<Foo> implements Foo {

	@Override
	public ListenableFuture<String> doThing() {
		return Futures.immediateFuture("ehatever");
	}
}
