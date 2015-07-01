package ph.actors.root;

import java.util.concurrent.CompletionStage;

/**
 * TODO
 */
public interface Root {

	public abstract CompletionStage<Boolean> start();
}
