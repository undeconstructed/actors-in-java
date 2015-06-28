package ph.actors.root;

import com.google.common.util.concurrent.ListenableFuture;

/**
 * TODO
 */
public interface Root {

	public abstract ListenableFuture<Boolean> start();
}
