package org.apache.maven.surefire.booter;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.surefire.api.booter.MasterProcessChannelDecoder;
import org.apache.maven.surefire.api.booter.MasterProcessChannelEncoder;
import org.apache.maven.surefire.booter.spi.LegacyMasterProcessChannelDecoder;
import org.apache.maven.surefire.booter.spi.LegacyMasterProcessChannelEncoder;
import org.apache.maven.surefire.booter.spi.LegacyMasterProcessChannelProcessorFactory;
import org.apache.maven.surefire.booter.spi.SurefireMasterProcessChannelProcessorFactory;
import org.apache.maven.surefire.api.report.StackTraceWriter;
import org.apache.maven.surefire.shared.utils.cli.ShutdownHookUtils;
import org.apache.maven.surefire.spi.MasterProcessChannelProcessorFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.rules.ErrorCollector;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.nio.channels.ServerSocketChannel;

import static java.net.StandardSocketOptions.SO_KEEPALIVE;
import static java.net.StandardSocketOptions.SO_REUSEADDR;
import static java.net.StandardSocketOptions.TCP_NODELAY;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.Fail.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.powermock.api.mockito.PowerMockito.doAnswer;
import static org.powermock.api.mockito.PowerMockito.doCallRealMethod;
import static org.powermock.api.mockito.PowerMockito.doNothing;
import static org.powermock.api.mockito.PowerMockito.doThrow;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.verifyNoMoreInteractions;
import static org.powermock.api.mockito.PowerMockito.verifyPrivate;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.powermock.reflect.Whitebox.invokeMethod;
import static org.powermock.reflect.Whitebox.setInternalState;

/**
 * PowerMock tests for {@link ForkedBooter}.
 */
@RunWith( PowerMockRunner.class )
@PrepareForTest( {
                     PpidChecker.class,
                     ForkedBooter.class,
                     LegacyMasterProcessChannelEncoder.class,
                     ShutdownHookUtils.class
} )
@PowerMockIgnore( { "org.jacoco.agent.rt.*", "com.vladium.emma.rt.*" } )
public class ForkedBooterMockTest
{
    @Rule
    public final ErrorCollector errorCollector = new ErrorCollector();

    @Mock
    private PpidChecker pluginProcessChecker;

    @Mock
    private ForkedBooter booter;

    @Mock
    private MasterProcessChannelProcessorFactory channelProcessorFactory;

    @Mock
    private LegacyMasterProcessChannelEncoder eventChannel;

    @Captor
    private ArgumentCaptor<StackTraceWriter> capturedStackTraceWriter;

    @Captor
    private ArgumentCaptor<Boolean> capturedBoolean;

    @Captor
    private ArgumentCaptor<String[]> capturedArgs;

    @Captor
    private ArgumentCaptor<ForkedBooter> capturedBooter;

    @Test
    public void shouldCheckNewPingMechanism() throws Exception
    {
        boolean canUse = invokeMethod( ForkedBooter.class, "canUseNewPingMechanism", (PpidChecker) null );
        assertThat( canUse ).isFalse();

        when( pluginProcessChecker.canUse() ).thenReturn( false );
        canUse = invokeMethod( ForkedBooter.class, "canUseNewPingMechanism", pluginProcessChecker );
        assertThat( canUse ).isFalse();

        when( pluginProcessChecker.canUse() ).thenReturn( true );
        canUse = invokeMethod( ForkedBooter.class, "canUseNewPingMechanism", pluginProcessChecker );
        assertThat( canUse ).isTrue();
    }

    @Test
    public void testMain() throws Exception
    {
        mockStatic( ForkedBooter.class );

        doCallRealMethod()
                .when( ForkedBooter.class, "run", capturedBooter.capture(), capturedArgs.capture() );

        String[] args = new String[]{ "/", "dump", "surefire.properties", "surefire-effective.properties" };
        invokeMethod( ForkedBooter.class, "run", booter, args );

        assertThat( capturedBooter.getAllValues() )
                .hasSize( 1 )
                .contains( booter );

        assertThat( capturedArgs.getAllValues() )
                .hasSize( 1 );
        assertThat( capturedArgs.getAllValues().get( 0 )[0] )
                .isEqualTo( "/" );
        assertThat( capturedArgs.getAllValues().get( 0 )[1] )
                .isEqualTo( "dump" );
        assertThat( capturedArgs.getAllValues().get( 0 )[2] )
                .isEqualTo( "surefire.properties" );
        assertThat( capturedArgs.getAllValues().get( 0 )[3] )
                .isEqualTo( "surefire-effective.properties" );

        verifyPrivate( booter, times( 1 ) )
                .invoke( "setupBooter", same( args[0] ), same( args[1] ), same( args[2] ), same( args[3] ) );

        verifyPrivate( booter, times( 1 ) )
                .invoke( "execute" );

        verifyNoMoreInteractions( booter );
    }

    @Test
    public void testMainWithError() throws Exception
    {
        mockStatic( ForkedBooter.class );

        doCallRealMethod()
                .when( ForkedBooter.class, "run", any( ForkedBooter.class ), any( String[].class ) );

        doThrow( new RuntimeException( "dummy exception" ) )
                .when( booter, "execute" );

        doNothing()
                .when( booter, "setupBooter",
                        any( String.class ), any( String.class ), any( String.class ), any( String.class ) );

        setInternalState( booter, "eventChannel", eventChannel );

        String[] args = new String[]{ "/", "dump", "surefire.properties", "surefire-effective.properties" };
        invokeMethod( ForkedBooter.class, "run", booter, args );

        verifyPrivate( booter, times( 1 ) )
                .invoke( "setupBooter", same( args[0] ), same( args[1] ), same( args[2] ), same( args[3] ) );

        verifyPrivate( booter, times( 1 ) )
                .invoke( "execute" );

        verify( eventChannel, times( 1 ) )
                .consoleErrorLog( capturedStackTraceWriter.capture(), capturedBoolean.capture() );
        assertThat( capturedStackTraceWriter.getValue() )
                .isNotNull();
        assertThat( capturedStackTraceWriter.getValue().smartTrimmedStackTrace() )
                .isEqualTo( "test subsystem#no method RuntimeException dummy exception" );
        assertThat( capturedStackTraceWriter.getValue().getThrowable().getTarget() )
                .isNotNull()
                .isInstanceOf( RuntimeException.class );
        assertThat( capturedStackTraceWriter.getValue().getThrowable().getTarget().getMessage() )
                .isEqualTo( "dummy exception" );

        verifyPrivate( booter, times( 1 ) )
                .invoke( "cancelPingScheduler" );

        verifyPrivate( booter, times( 1 ) )
                .invoke( "exit1" );

        verifyNoMoreInteractions( booter );
    }

    @Test
    public void shouldNotCloseChannelProcessorFactory() throws Exception
    {
        setInternalState( booter, "channelProcessorFactory", (MasterProcessChannelProcessorFactory) null );

        doCallRealMethod()
            .when( booter, "closeForkChannel" );

        invokeMethod( booter, "closeForkChannel" );

        verifyZeroInteractions( channelProcessorFactory );
    }

    @Test
    public void shouldCloseChannelProcessorFactory() throws Exception
    {
        setInternalState( booter, "channelProcessorFactory", channelProcessorFactory );

        doCallRealMethod()
            .when( booter, "closeForkChannel" );

        invokeMethod( booter, "closeForkChannel" );

        verify( channelProcessorFactory, times( 1 ) )
            .close();
        verifyNoMoreInteractions( channelProcessorFactory );
    }

    @Test
    public void shouldFailOnCloseChannelProcessorFactory() throws Exception
    {
        setInternalState( booter, "channelProcessorFactory", channelProcessorFactory );

        doThrow( new IOException() )
            .when( channelProcessorFactory )
            .close();

        doCallRealMethod()
            .when( booter, "closeForkChannel" );

        invokeMethod( booter, "closeForkChannel" );

        verify( channelProcessorFactory, times( 1 ) )
            .close();
        verifyNoMoreInteractions( channelProcessorFactory );
    }

    @Test
    public void shouldLookupLegacyDecoderFactory() throws Exception
    {
        mockStatic( ForkedBooter.class );

        doCallRealMethod()
            .when( ForkedBooter.class, "lookupDecoderFactory", anyString() );

        try ( final MasterProcessChannelProcessorFactory factory =
                  invokeMethod( ForkedBooter.class, "lookupDecoderFactory", "pipe://3" ) )
        {
            assertThat( factory ).isInstanceOf( LegacyMasterProcessChannelProcessorFactory.class );

            assertThat( factory.canUse( "pipe://3" ) ).isTrue();

            assertThat( factory.canUse( "-- whatever --" ) ).isFalse();

            errorCollector.checkThrows( MalformedURLException.class, new ThrowingRunnable()
            {
                @Override
                public void run() throws Throwable
                {
                    factory.connect( "tcp://localhost:123" );
                    fail();
                }
            } );

            factory.connect( "pipe://3" );

            MasterProcessChannelDecoder decoder = factory.createDecoder();
            assertThat( decoder ).isInstanceOf( LegacyMasterProcessChannelDecoder.class );
            MasterProcessChannelEncoder encoder = factory.createEncoder();
            assertThat( encoder ).isInstanceOf( LegacyMasterProcessChannelEncoder.class );
        }
    }

    @Test
    public void shouldLookupSurefireDecoderFactory() throws Exception
    {
        mockStatic( ForkedBooter.class );

        doCallRealMethod()
            .when( ForkedBooter.class, "lookupDecoderFactory", anyString() );

        try ( ServerSocketChannel server = ServerSocketChannel.open() )
        {
            if ( server.supportedOptions().contains( SO_REUSEADDR ) )
            {
                server.setOption( SO_REUSEADDR, true );
            }

            if ( server.supportedOptions().contains( TCP_NODELAY ) )
            {
                server.setOption( TCP_NODELAY, true );
            }

            if ( server.supportedOptions().contains( SO_KEEPALIVE ) )
            {
                server.setOption( SO_KEEPALIVE, true );
            }

            server.bind( new InetSocketAddress( 0 ) );
            int serverPort = ( (InetSocketAddress) server.getLocalAddress() ).getPort();

            try ( MasterProcessChannelProcessorFactory factory =
                     invokeMethod( ForkedBooter.class, "lookupDecoderFactory", "tcp://127.0.0.1:" + serverPort ) )
            {
                assertThat( factory )
                    .isInstanceOf( SurefireMasterProcessChannelProcessorFactory.class );

                assertThat( factory.canUse( "tcp://127.0.0.1:" + serverPort ) )
                    .isTrue();

                assertThat( factory.canUse( "-- whatever --" ) )
                    .isFalse();

                errorCollector.checkThrows( MalformedURLException.class, new ThrowingRunnable()
                {
                    @Override
                    public void run() throws Throwable
                    {
                        factory.connect( "pipe://1" );
                        fail();
                    }
                } );

                errorCollector.checkThrows( IOException.class, new ThrowingRunnable()
                {
                    @Override
                    public void run() throws Throwable
                    {
                        factory.connect( "tcp://localhost:123\u0000\u0000\u0000" );
                        fail();
                    }
                } );

                factory.connect( "tcp://127.0.0.1:" + serverPort );

                MasterProcessChannelDecoder decoder = factory.createDecoder();
                assertThat( decoder )
                    .isInstanceOf( LegacyMasterProcessChannelDecoder.class );
                MasterProcessChannelEncoder encoder = factory.createEncoder();
                assertThat( encoder )
                    .isInstanceOf( LegacyMasterProcessChannelEncoder.class );
            }
        }
    }

    @Test
    public void testFlushEventChannelOnExit() throws Exception
    {
        mockStatic( ShutdownHookUtils.class );

        final MasterProcessChannelEncoder eventChannel = mock( MasterProcessChannelEncoder.class );
        ForkedBooter booter = new ForkedBooter();
        setInternalState( booter, "eventChannel", eventChannel );

        doAnswer( new Answer<Object>()
        {
            @Override
            public Object answer( InvocationOnMock invocation )
            {
                Thread t = invocation.getArgument( 0 );
                assertThat( t.isDaemon() ).isTrue();
                t.run();
                verify( eventChannel, times( 1 ) ).onJvmExit();
                return null;
            }
        } ).when( ShutdownHookUtils.class, "addShutDownHook", any( Thread.class ) );
        invokeMethod( booter, "flushEventChannelOnExit" );
    }
}
