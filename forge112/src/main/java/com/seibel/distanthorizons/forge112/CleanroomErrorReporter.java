package com.seibel.distanthorizons.cleanroom;

public class CleanroomErrorReporter
{
	static void printOnShutdown(final String message)
	{
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				System.err.println(message);
			}
		}));
	}
}