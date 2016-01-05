/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.wifi;

import static org.junit.Assert.assertTrue;

import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Creates a looper whose message queue can be manipulated
 * This allows testing code that uses a looper to dispatch messages in a deterministic manner
 * Creating a MockLooper will also install it as the looper for the current thread
 */
public class MockLooper {
    private final Looper mLooper;

    private static final Constructor<Looper> LOOPER_CONSTRUCTOR;
    private static final Field THREAD_LOCAL_LOOPER_FIELD;
    private static final Method MESSAGE_QUEUE_NEXT_METHOD;

    static {
        try {
            LOOPER_CONSTRUCTOR = Looper.class.getDeclaredConstructor(Boolean.TYPE);
            LOOPER_CONSTRUCTOR.setAccessible(true);
            THREAD_LOCAL_LOOPER_FIELD = Looper.class.getDeclaredField("sThreadLocal");
            THREAD_LOCAL_LOOPER_FIELD.setAccessible(true);
            MESSAGE_QUEUE_NEXT_METHOD = MessageQueue.class.getDeclaredMethod("next");
            MESSAGE_QUEUE_NEXT_METHOD.setAccessible(true);
        } catch (NoSuchFieldException | NoSuchMethodException e) {
            throw new RuntimeException("Failed to initialize MockLooper", e);
        }
    }


    public MockLooper() throws Exception {
        mLooper = LOOPER_CONSTRUCTOR.newInstance(false);

        ThreadLocal<Looper> threadLocalLooper =
                (ThreadLocal<Looper>) THREAD_LOCAL_LOOPER_FIELD.get(null);
        threadLocalLooper.set(mLooper);
    }

    public Looper getLooper() {
        return mLooper;
    }

    private Message messageQueueNext() {
        try {
            return (Message) MESSAGE_QUEUE_NEXT_METHOD.invoke(mLooper.getQueue());
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Reflection error when getting next message", e);
        }
    }

    /**
     * @return true if there are pending messages in the message queue
     */
    public boolean hasMessage() {
        return !mLooper.getQueue().isIdle();
    }

    /**
     * @return the next message in the Looper's message queue or null if there is none
     */
    public Message nextMessage() {
        if (hasMessage()) {
            return messageQueueNext();
        } else {
            return null;
        }
    }

    /**
     * Dispatch the next message in the queue
     * Asserts that there is a message in the queue
     */
    public void dispatchNext() {
        assertTrue(hasMessage());
        Message msg = messageQueueNext();
        if (msg == null) {
            return;
        }
        msg.getTarget().dispatchMessage(msg);
    }

    /**
     * Dispatch all messages currently in the queue
     * Will not fail if there are no messages pending
     * @return the number of messages dispatched
     */
    public int dispatchAll() {
        int count = 0;
        while (hasMessage()) {
            dispatchNext();
            ++count;
        }
        return count;
    }
}
