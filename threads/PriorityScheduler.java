package nachos.threads;

import nachos.machine.*;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;

/**
 * A scheduler that chooses threads based on their priorities.
 * 
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the thread
 * that has been waiting longest.
 * 
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has the
 * potential to starve a thread if there's always a thread waiting with higher
 * priority.
 * 
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
	/**
	 * Allocate a new priority scheduler.
	 */
	public PriorityScheduler() {
	}

	/**
	 * Allocate a new priority thread queue.
	 * 
	 * @param transferPriority <tt>true</tt> if this queue should transfer
	 * priority from waiting threads to the owning thread.
	 * @return a new priority thread queue.
	 */
	public ThreadQueue newThreadQueue(boolean transferPriority) {
		return new PriorityQueue(transferPriority);
	}

	public int getPriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getPriority();
	}

	public int getEffectivePriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getEffectivePriority();
	}

	public void setPriority(KThread thread, int priority) {
		Lib.assertTrue(Machine.interrupt().disabled());

		Lib.assertTrue(priority >= priorityMinimum
				&& priority <= priorityMaximum);

		getThreadState(thread).setPriority(priority);
	}

	public boolean increasePriority() {
		boolean intStatus = Machine.interrupt().disable();
		boolean ret = true;

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMaximum)
			ret = false;
		else
			setPriority(thread, priority + 1);

		Machine.interrupt().restore(intStatus);
		return ret;
	}

	public boolean decreasePriority() {
		boolean intStatus = Machine.interrupt().disable();
		boolean ret = true;

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMinimum)
			ret = false;
		else
			setPriority(thread, priority - 1);

		Machine.interrupt().restore(intStatus);
		return ret;
	}

	/**
	 * The default priority for a new thread. Do not change this value.
	 */
	public static final int priorityDefault = 1;

	/**
	 * The minimum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMinimum = 0;

	/**
	 * The maximum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMaximum = 7;

	/**
	 * Return the scheduling state of the specified thread.
	 * 
	 * @param thread the thread whose scheduling state to return.
	 * @return the scheduling state of the specified thread.
	 */
	protected ThreadState getThreadState(KThread thread) {
		if (thread.schedulingState == null)
			thread.schedulingState = new ThreadState(thread);

		return (ThreadState) thread.schedulingState;
	}

	/**
	 * A <tt>ThreadQueue</tt> that sorts threads by priority.
	 */
	protected class PriorityQueue extends ThreadQueue {
		PriorityQueue(boolean transferPriority) {
			this.transferPriority = transferPriority;
		}

		public void waitForAccess(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).waitForAccess(this);
		}

		public void acquire(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			
			
			// if have a holder and is transferring priority, 
			// remove itself from ThreadResourceQueue 
			if((this.holder != null) && this.transferPriority){
				this.holder.ThreadResourceQueue.remove(this);
			}
			
			this.holder = getThreadState(thread);
			
			getThreadState(thread).acquire(this);
		}

		public KThread nextThread() {
			Lib.assertTrue(Machine.interrupt().disabled());
			
			// if have a holder and is transferring priority, remove itself from ThreadResourceQueue
			if((this.holder != null) && this.transferPriority){
				this.holder.ThreadResourceQueue.remove(this);
			}
			
			// if waitQueue is empty, return null
			if(waitQueue.isEmpty()){
				return null;
			}
			
			// else remove nextThread from waitQueue and return it
			else {
				
				ThreadState nextThread = pickNextThread();
				waitQueue.remove(nextThread);
				nextThread.acquire(this);
				
				return nextThread.thread;
			}
		}

		/**
		 * Return the next thread that <tt>nextThread()</tt> would return,
		 * without modifying the state of this queue.
		 * 
		 * @return the next thread that <tt>nextThread()</tt> would return.
		 */
		protected ThreadState pickNextThread() {
			
			// if waitQueue of ThreadState is empty, return null
			if(waitQueue.isEmpty()){
				return null;
			}
			
			// else return the next ThreadState to be returned from the waitQueue
			else{
				
				/// ThreadState to return
				ThreadState returnTS = null;
				
				// iterate through the waitQueue ad select the ThreadState with highest priority
				ListIterator<ThreadState> itr = waitQueue.listIterator();
				while(itr.hasNext()){
					ThreadState curr = itr.next();
					
					// if the current ThreadState has a higher priority, select it to be the returnTS
					if((returnTS == null) || curr.priority > returnTS.priority){
						returnTS = curr;
					}
				}
				
				return returnTS;
			}
		}
		
		public void setNeedPriorityChange() {
			Lib.assertTrue(Machine.interrupt().disabled());
			
			// if not transferring priority then return
			if(!transferPriority) {
				return;
			}
			
			else{
				this.NeedPriorityChange = true;
				
				// if have a holder, call setNeedPriorityChange
				if(this.holder != null) {
					holder.setNeedPriorityChange();
				}
			}
		}
		
		public int getEffectivePriority() {
			Lib.assertTrue(Machine.interrupt().disabled());
			
			// if not transferring priority return minimum priority
			if(!transferPriority) {
				return priorityMinimum;
			}
			
			// if needs to change priority
			if(NeedPriorityChange) {
				this.effective = priorityMinimum;
				
				// iterate through each ThreadState in waitQueue
				ListIterator<ThreadState> itr = waitQueue.listIterator();
				while(itr.hasNext()) {
					ThreadState curr = itr.next();
					this.effective = Math.max(this.effective, curr.getEffectivePriority());
				}

				this.NeedPriorityChange = false;
			}
			
			return effective;
		}

		public void print() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me (if you want)
		}
		

		/**
		 * <tt>true</tt> if this queue should transfer priority from waiting
		 * threads to the owning thread.
		 */
		public boolean transferPriority;
		
		// holder - Holder of the resource
		private ThreadState holder = null;
		
		// waitQueue - queue of ThreadStates waiting on this resource
		private LinkedList<ThreadState> waitQueue = new LinkedList<ThreadState>();
		
		// priotyChange - set true when a new thread is added to the queue or when any queue sets itself to NeedPriorityChange
		private boolean NeedPriorityChange;  
		
		// effective - the cached highest of the effective priorities in the waitQueue
		private int effective;
				
	}

	/**
	 * The scheduling state of a thread. This should include the thread's
	 * priority, its effective priority, any objects it owns, and the queue it's
	 * waiting for, if any.
	 * 
	 * @see nachos.threads.KThread#schedulingState
	 */
	protected class ThreadState {
		/**
		 * Allocate a new <tt>ThreadState</tt> object and associate it with the
		 * specified thread.
		 * 
		 * @param thread the thread this state belongs to.
		 */
		public ThreadState(KThread thread) {
			this.thread = thread;

			setPriority(priorityDefault);
		}

		/**
		 * Return the priority of the associated thread.
		 * 
		 * @return the priority of the associated thread.
		 */
		public int getPriority() {
			return priority;
		}

		/**
		 * Return the effective priority of the associated thread.
		 * 
		 * @return the effective priority of the associated thread.
		 */
		public int getEffectivePriority() {
			
			this.effective = this.priority;
			
			if(NeedPriorityChange) {
				
				Iterator<ThreadQueue> itr = ThreadResourceQueue.listIterator();
				while(itr.hasNext()) {
					PriorityQueue curr = (PriorityQueue)itr.next();
					effective =  Math.max(effective, curr.getEffectivePriority());
				}
			}
			
			return effective;
		}

		/**
		 * Set the priority of the associated thread to the specified value.
		 * 
		 * @param priority the new priority.
		 */
		public void setPriority(int priority) {
			if (this.priority == priority)
				return;

			this.priority = priority;

			setNeedPriorityChange();
		}

		/**
		 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
		 * the associated thread) is invoked on the specified priority queue.
		 * The associated thread is therefore waiting for access to the resource
		 * guarded by <tt>waitQueue</tt>. This method is only called if the
		 * associated thread cannot immediately obtain access.
		 * 
		 * @param waitQueue the queue that the associated thread is now waiting
		 * on.
		 * 
		 * @see nachos.threads.ThreadQueue#waitForAccess
		 */
		public void waitForAccess(PriorityQueue waitQueue) {
			
			Lib.assertTrue(Machine.interrupt().disabled());
			Lib.assertTrue(waitQueue.waitQueue.indexOf(thread) == -1);
			
			// adding waitQueue to waitingOn list
			waitQueue.waitQueue.add(getThreadState(thread));
			waitQueue.setNeedPriorityChange();
			
			waitingOn = waitQueue;
			
			// if waitQueue was previously in ThreadResourceQueue, remove it and set its holder to null
			if(ThreadResourceQueue.indexOf(waitQueue) != -1) {
				ThreadResourceQueue.remove(waitQueue);
				waitQueue.holder = null;
			}
		}

		/**
		 * Called when the associated thread has acquired access to whatever is
		 * guarded by <tt>waitQueue</tt>. This can occur either as a result of
		 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
		 * <tt>thread</tt> is the associated thread), or as a result of
		 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
		 * 
		 * @see nachos.threads.ThreadQueue#acquire
		 * @see nachos.threads.ThreadQueue#nextThread
		 */
		public void acquire(PriorityQueue waitQueue) {
			
			// add waitQueue to ThreadResourceQueue
			ThreadResourceQueue.add(waitQueue);
			
			// if waitingOn list contains waitQueue, remove it from the list
			if(waitingOn ==  waitQueue) {
				waitingOn = null;
			}
			
			setNeedPriorityChange();
		}
		
		public void setNeedPriorityChange() {
			
			// if already set, return
			if(NeedPriorityChange) {
				return;
			}
			else {
				NeedPriorityChange = true;
				
				// for each PriorityQueue in waitingOn call setNeedPriorityChange
				PriorityQueue set = (PriorityQueue)waitingOn;
				if (set != null){
					set.setNeedPriorityChange();
				}
			}		
		}

		/** The thread with which this object is associated. */
		protected KThread thread;

		/** The priority of the associated thread. */
		protected int priority;
		
		
		// ThreadResourceQueue - Holds PriorityQueues. Signifies resources that this thread currently holds
		protected LinkedList<ThreadQueue> ThreadResourceQueue = new LinkedList<ThreadQueue>();
		
		// NeedPriorityChange - set to true when thread's priority is changed
		protected boolean NeedPriorityChange;
		
		// waitingOn - Holds PriorityQueues corresponding to resources that this thread has attempted to acquire but could not
		protected ThreadQueue waitingOn;
		
		// effective - the cached effective priority of this thread
		protected int effective;
		
	}
}
