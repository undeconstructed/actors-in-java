package ph.actors.bar;

import java.util.concurrent.CompletionStage;

/**
 * TODO
 */
public interface Bar {

	public CompletionStage<Integer> plus1(int n);

}
