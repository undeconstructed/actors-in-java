package ph.actors.actors;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.Executors;

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
}

/**
 * TODO
 */
class Cue {

	final UUID rid;
	final Key target;
	final String method;
	final Object data;

	public Cue(UUID rid, Key target, String method, Object data) {
		this.rid = rid;
		this.target = target;
		this.method = method;
		this.data = data;
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
		private Map<String, Method> methods = new HashMap<>();

		Role(String name, Class i, Class t) {
			this.name = name;
			this.i = i;
			this.t = t;

			for (Method m : i.getDeclaredMethods()) {
				if (m.getReturnType() != ListenableFuture.class) {
					throw new IllegalArgumentException("actor methods must return futures");
				}
				methods.put(m.getName(), m);
			}
		}

		/**
		 * @return
		 */
		Actor newActor() {
			try {
				return (Actor) t.newInstance();
			} catch (InstantiationException | IllegalAccessException e) {
				throw new RuntimeException(e);
			}
		}

		void perform(Actor actor, String method, Object data) {
			try {
				methods.get(method).invoke(actor);
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				throw new RuntimeException(e);
			}
		}
	}

	class Part {

		final Role role;
		final Actor actor;
		private final Queue<Cue> cues = new LinkedList<>();

		private boolean resting = true;

		Part(Role role, Actor actor) {
			this.role = role;
			this.actor = actor;
		}

		void performOn(Cue cue) {
			if (resting) {
				resting = false;
				performOn1(cue);
			} else {
				cues.add(cue);
			}
		}

		private void performOn1(Cue cue) {
			ListenableFuture<?> f = pool.submit(() -> {
				role.perform(actor, cue.method, cue.data);
			});
			f.addListener(() -> {
				Cue nextCue = cues.poll();
				if (nextCue != null) {
					performOn1(cue);
				} else {
					resting = true;
				}
			}, thread);
		}
	}

	private long counter = 0;
	private Map<String, Role> roles = new HashMap<>();
	private Map<Key, Part> parts = new HashMap<>();
	private ListeningExecutorService thread;
	private ListeningExecutorService pool;

	/**
	 * 
	 */
	public Stage() {
	}

	public Role addRole(Class t) {
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
			long number = counter++;
			Key key = new Key(bootRole.name, number);
			prompt(new Cue(UUID.randomUUID(), key, "start", null));

			return true;
		});
	}

	/**
	 * @param cue
	 */
	private void prompt(Cue cue) {
		Part part = parts.get(cue.target);
		if (part == null) {
			Role role = roles.get(cue.target.type);
			Actor actor = role.newActor();
			actor.setup(cue.target, this);

			part = new Part(role, actor);
			parts.put(cue.target, part);
		}
		part.performOn(cue);
	}

	<I> I find(Class<I> i, long id) {
		return (I) Proxy.newProxyInstance(Stage.class.getClassLoader(), new Class[] { i }, (proxy, method, args) -> {
			ListenableFuture f = SettableFuture.create();
			UUID rid = UUID.randomUUID();
			thread.submit(() -> {

			});
			return f;
		});
	}
}
