package ph.actors.bar;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import ph.actors.actors.Actor;

/**
 * TODO
 */
public class BarActor extends Actor<Bar> implements Bar {

	@Override
	public CompletionStage<Integer> plus1(int n) {
		return CompletableFuture.completedFuture(n + 1);
	}
}
