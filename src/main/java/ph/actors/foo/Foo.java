package ph.actors.foo;

import java.util.concurrent.CompletionStage;

/**
 * TODO
 */
public interface Foo {

	public CompletionStage<String> doThing(int times);
}
