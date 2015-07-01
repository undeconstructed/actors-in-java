package ph.actors.actors;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

/**
 * TODO
 */
class Key {

	final String type;
	final Long number;

	public Key(String type, Long number) {
		this.type = type;
		this.number = number;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((number == null) ? 0 : number.hashCode());
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Key other = (Key) obj;
		if (number == null) {
			if (other.number != null)
				return false;
		} else if (!number.equals(other.number))
			return false;
		if (type == null) {
			if (other.type != null)
				return false;
		} else if (!type.equals(other.type))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return String.format("Key [type=%s, number=%s]", type, number);
	}
}

/**
 * TODO
 */
class Request {

	final UUID rid;
	final Key caller;
	final Key callee;
	final String method;
	final Object[] data;

	public Request(UUID rid, Key source, Key target, String method, Object[] data) {
		this.rid = rid;
		this.caller = source;
		this.callee = target;
		this.method = method;
		this.data = data;
	}

	@Override
	public String toString() {
		return String.format("Request [rid=%s, caller=%s, callee=%s, method=%s, data=%s]", rid, caller, callee, method,
				Arrays.toString(data));
	}
}

/**
 * TODO
 */
class Response {

	final UUID rid;
	final Key caller;
	final Throwable error;
	final Object data;

	public Response(UUID rid, Key source, Throwable error, Object data) {
		this.rid = rid;
		this.caller = source;
		this.error = error;
		this.data = data;
	}

	@Override
	public String toString() {
		return String.format("Response [rid=%s, caller=%s, error=%s, data=%s]", rid, caller, error, data);
	}
}

/**
 *
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class Platform {

	private static final Key PLATFORM_KEY = new Key("", -1l);

	/**
	 * TODO
	 */
	class ActorType {

		final String name;
		final Class i;
		final Class t;
		private Constructor c;
		private Map<String, Method> methods = new HashMap<>();

		ActorType(String name, Class i, Class t) throws Exception {
			this.name = name;
			this.i = i;
			this.t = t;
			this.c = t.getConstructor();
			this.c.setAccessible(true);

			for (Method m : i.getDeclaredMethods()) {
				if (m.getReturnType() != CompletionStage.class) {
					throw new IllegalArgumentException("actor methods must return completion stages");
				}
				m.setAccessible(true);
				methods.put(m.getName(), m);
			}
		}

		/**
		 * @return
		 */
		Actor newActor() {
			try {
				return (Actor) c.newInstance();
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException e) {
				e.printStackTrace();
				System.exit(1);
				return null;
			}
		}

		CompletionStage perform(Actor actor, String method, Object[] data) throws InvocationTargetException {
			try {
				return (CompletionStage) methods.get(method).invoke(actor, data);
			} catch (IllegalAccessException | IllegalArgumentException e) {
				e.printStackTrace();
				System.exit(1);
				return null;
			}
		}
	}

	class LiveActor {

		final ActorType role;
		final Actor actor;
		private final Queue<Request> cues = new LinkedList<>();

		private boolean resting = true;

		LiveActor(ActorType actorType, Actor actor) {
			this.role = actorType;
			this.actor = actor;
		}

		void service(Request call) {
			if (resting) {
				resting = false;
				service1(call);
			} else {
				cues.add(call);
			}
		}

		private void service1(Request call) {
			ForkJoinPool.commonPool().execute(() -> {
				CompletionStage<Object> c;
				try {
					c = role.perform(actor, call.method, call.data);
				} catch (InvocationTargetException e) {
					sendResponse(call.rid, call.caller, e.getCause(), null);
					return;
				} catch (Exception e) {
					sendResponse(call.rid, call.caller, e, null);
					return;
				}
				c.exceptionally(t -> {
					if (t instanceof ExecutionException) {
						sendResponse(call.rid, call.caller, t.getCause(), null);
					} else {
						sendResponse(call.rid, call.caller, t, null);
					}
					return null;
				});
				c.thenApply(r -> {
					// get result back to caller
					sendResponse(call.rid, call.caller, null, r);
					return null;
				});
				c.thenRunAsync(() -> {
					Request nextCue = cues.poll();
					if (nextCue != null) {
						service1(call);
					} else {
						resting = true;
					}
				}, thread);
			});
		}
	}

	private Map<String, ActorType> actorTypes = new HashMap<>();
	private Map<Key, LiveActor> liveActors = new HashMap<>();
	private Map<UUID, CompletableFuture> pending = new HashMap<>();
	private ExecutorService thread;

	/**
	 * 
	 */
	public Platform() {
	}

	public ActorType addActorType(Class t) throws Exception {
		ParameterizedType actorType = (ParameterizedType) t.getGenericSuperclass();
		if (actorType.getRawType() != Actor.class) {
			throw new IllegalArgumentException("role must played by actor");
		}
		Class iface = (Class) actorType.getActualTypeArguments()[0];
		if (!iface.isAssignableFrom(t)) {
			throw new IllegalArgumentException("actor must obey its contract");
		}
		String name = iface.getSimpleName();
		ActorType role = new ActorType(name, iface, t);
		actorTypes.put(name, role);
		return role;
	}

	public void start(ActorType bootActorType) {
		thread = Executors.newSingleThreadExecutor();

		System.out.println("p " + ForkJoinPool.commonPool().getParallelism());

		thread.submit(() -> {
			Key key = new Key(bootActorType.name, 1l);
			handleRequest(new Request(UUID.randomUUID(), PLATFORM_KEY, key, "start", null));
		});
	}

	/**
	 * Called by actors to get a proxy on another actor.
	 */
	<I> I find(Key local, Class<I> i, long id) {
		Key remote = new Key(i.getSimpleName(), id);
		return (I) Proxy.newProxyInstance(Platform.class.getClassLoader(), new Class[] { i },
				(proxy, method, args) -> {
					CompletableFuture f = new CompletableFuture<>();
					sendRequest(local, remote, f, method.getName(), args);
					return f;
				});
	}

	/**
	 * Called from an actor to send off a request.
	 */
	private void sendRequest(Key local, Key remote, CompletableFuture f, String method, Object[] data) {
		UUID rid = UUID.randomUUID();
		Request call = new Request(rid, local, remote, method, data);
		thread.submit(() -> {
			pending.put(rid, f);
			// this should be a network handoff here
			handleRequest(call);
		});
	}

	/**
	 * Called internally to schedule some work.
	 */
	private void handleRequest(Request request) {
		System.out.println("Received request: " + request);
		LiveActor liveActor = liveActors.get(request.callee);
		if (liveActor == null) {
			ActorType actorType = actorTypes.get(request.callee.type);
			Actor actor = actorType.newActor();
			actor.setup(request.callee, this);

			liveActor = new LiveActor(actorType, actor);
			liveActors.put(request.callee, liveActor);
		}
		liveActor.service(request);
	}

	/**
	 * Called (implicitly) from an actor to send a response.
	 */
	private void sendResponse(UUID rid, Key source, Throwable error, Object data) {
		Response response = new Response(rid, source, error, data);
		thread.submit(() -> {
			// this should be a network handoff here
			handleResponse(response);
		});
	}

	/**
	 * Called internally to wake up something.
	 */
	private void handleResponse(Response response) {
		System.out.println("Received response: " + response);
		if (response.caller.equals(PLATFORM_KEY)) {
			System.out.println("Platform receives: " + response);
		} else {
			CompletableFuture f = pending.remove(response.rid);
			if (f != null) {
				ForkJoinPool.commonPool().execute(() -> {
					if (response.data != null) {
						f.complete(response.data);
					} else {
						f.completeExceptionally(response.error);
					}
				});
			}
		}
	}
}
