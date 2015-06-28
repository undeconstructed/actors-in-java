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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

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
class Call {

	final UUID rid;
	final Key source;
	final Key target;
	final String method;
	final Object[] data;

	public Call(UUID rid, Key source, Key target, String method, Object[] data) {
		this.rid = rid;
		this.source = source;
		this.target = target;
		this.method = method;
		this.data = data;
	}

	@Override
	public String toString() {
		return String.format("Call [rid=%s, source=%s, target=%s, method=%s, data=%s]", rid, source, target, method,
				Arrays.toString(data));
	}
}

/**
 * TODO
 */
class Response {

	final UUID rid;
	final Key source;
	final Throwable error;
	final Object data;

	public Response(UUID rid, Key source, Throwable error, Object data) {
		this.rid = rid;
		this.source = source;
		this.error = error;
		this.data = data;
	}

	@Override
	public String toString() {
		return String.format("Response [rid=%s, source=%s, error=%s, data=%s]", rid, source, error, data);
	}
}

/**
 *
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class Stage {

	/**
	 * TODO
	 */
	class Role {

		final String name;
		final Class i;
		final Class t;
		private Constructor c;
		private Map<String, Method> methods = new HashMap<>();

		Role(String name, Class i, Class t) throws Exception {
			this.name = name;
			this.i = i;
			this.t = t;
			this.c = t.getConstructor();
			this.c.setAccessible(true);

			for (Method m : i.getDeclaredMethods()) {
				if (m.getReturnType() != ListenableFuture.class) {
					throw new IllegalArgumentException("actor methods must return futures");
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
				throw new RuntimeException(e);
			}
		}

		Object perform(Actor actor, String method, Object data) throws InvocationTargetException {
			try {
				return methods.get(method).invoke(actor);
			} catch (IllegalAccessException | IllegalArgumentException e) {
				throw new RuntimeException(e);
			}
		}
	}

	class Part {

		final Role role;
		final Actor actor;
		private final Queue<Call> cues = new LinkedList<>();

		private boolean resting = true;

		Part(Role role, Actor actor) {
			this.role = role;
			this.actor = actor;
		}

		void service(Call call) {
			if (resting) {
				resting = false;
				service1(call);
			} else {
				cues.add(call);
			}
		}

		private void service1(Call call) {
			ListenableFuture f1 = pool.submit(() -> role.perform(actor, call.method, call.data));
			ListenableFuture f2 = Futures.dereference(f1);
			// listener to get result back to caller
			f2.addListener(() -> {
				try {
					Object data = f2.get();
					respond(new Response(call.rid, call.source, null, data));
				} catch (ExecutionException e) {
					respond(new Response(call.rid, call.source, e.getCause(), null));
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}, thread);
			// listener to get next job running
			f2.addListener(() -> {
				Call nextCue = cues.poll();
				if (nextCue != null) {
					service1(call);
				} else {
					resting = true;
				}
			}, thread);
		}
	}

	private Map<String, Role> roles = new HashMap<>();
	private Map<Key, Part> parts = new HashMap<>();
	private Map<UUID, SettableFuture> pending = new HashMap<>();
	private ListeningExecutorService thread;
	private ListeningExecutorService pool;

	/**
	 * 
	 */
	public Stage() {
	}

	public Role addRole(Class t) throws Exception {
		ParameterizedType actorType = (ParameterizedType) t.getGenericSuperclass();
		if (actorType.getRawType() != Actor.class) {
			throw new IllegalArgumentException("role must played by actor");
		}
		Class iface = (Class) actorType.getActualTypeArguments()[0];
		if (!iface.isAssignableFrom(t)) {
			throw new IllegalArgumentException("actor must obey its contract");
		}
		String name = iface.getSimpleName();
		Role role = new Role(name, iface, t);
		roles.put(name, role);
		return role;
	}

	public void start(Role bootRole) {
		thread = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
		pool = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());

		thread.submit(() -> {
			Key key = new Key(bootRole.name, 1l);
			call(new Call(UUID.randomUUID(), null, key, "start", null));
		});
	}

	/**
	 * Called internally to schedule some work.
	 */
	private void call(Call call) {
		System.out.println("Making call: " + call);
		Part part = parts.get(call.target);
		if (part == null) {
			Role role = roles.get(call.target.type);
			Actor actor = role.newActor();
			actor.setup(call.target, this);

			part = new Part(role, actor);
			parts.put(call.target, part);
		}
		part.service(call);
	}

	/**
	 * Called internally to return a value.
	 */
	private void respond(Response response) {
		SettableFuture f = pending.remove(response.rid);
		if (f != null) {
			if (response.data != null) {
				f.set(response.data);
			} else {
				f.setException(response.error);
			}
		}
	}

	/**
	 * Called by actors to get a proxy on another actor.
	 */
	<I> I find(Key local, Class<I> i, long id) {
		Key remote = new Key(i.getSimpleName(), id);
		return (I) Proxy.newProxyInstance(Stage.class.getClassLoader(), new Class[] { i }, (proxy, method, args) -> {
			SettableFuture f = SettableFuture.create();
			UUID rid = UUID.randomUUID();
			thread.submit(() -> makeCall(local, remote, rid, f, method.getName(), args));
			return f;
		});
	}

	/**
	 * Called internally to send off a request.
	 */
	private void makeCall(Key local, Key remote, UUID rid, SettableFuture f, String method, Object[] data) {
		pending.put(rid, f);
		// this will be a network handoff here
		call(new Call(rid, local, remote, method, data));
	}
}
