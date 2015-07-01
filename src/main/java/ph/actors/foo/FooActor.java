package ph.actors.foo;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import ph.actors.actors.Actor;
import ph.actors.bar.Bar;

/**
 * TODO
 */
public class FooActor extends Actor<Foo> implements Foo {

	@Override
	public CompletionStage<String> doThing(int times) {
		// throw new IllegalArgumentException("bad arg");

		List<CompletableFuture<Integer>> list = IntStream.range(0, times).mapToObj(i -> {
			return find(Bar.class, i).plus1(i).toCompletableFuture();
		}).collect(Collectors.toList());

		// List<CompletableFuture<Integer>> list = new ArrayList<>();
		// for (int i = 0; i < times; i++) {
		// list.add(find(Bar.class, i).plus1(i).toCompletableFuture());
		// }

		return CompletableFuture.allOf(list.toArray(new CompletableFuture[list.size()])).thenApply(v -> {
			List<Integer> numbers = list.stream().map(f -> f.join()).collect(Collectors.toList());
			return "I did it " + times + " times: " + numbers;
		});
	}
}
