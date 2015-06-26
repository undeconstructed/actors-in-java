package ph.actors.foo;

import com.google.common.util.concurrent.ListenableFuture;

/**
 * TODO
 */
public interface Foo {

	public ListenableFuture<String> doThing();
}
