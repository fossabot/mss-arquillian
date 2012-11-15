package org.mobicents.arquillian.mss.mediaserver.extension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.test.spi.TestClass;
import org.jboss.arquillian.test.spi.event.suite.After;
import org.jboss.arquillian.test.spi.event.suite.AfterClass;
import org.jboss.arquillian.test.spi.event.suite.Before;
import org.jboss.arquillian.test.spi.event.suite.BeforeClass;
import org.mobicents.arquillian.mediaserver.api.EmbeddedMediaserver;
import org.mobicents.arquillian.mediaserver.api.EndpointType;
import org.mobicents.arquillian.mediaserver.api.MediaserverConfMode;
import org.mobicents.arquillian.mediaserver.api.annotations.Mediaserver;

/**
 * MediaServerProducer is used along with the @Mediaserver annotation in order to start/stop and configure, or inject to a field,
 * a Mediaserver instance to the test class.
 *
 * The @Mediaserver annotation can be defined at:
 * 1) Class level -> The Mediaserver will be be started/stopped and configured automatically.
 * 2) Field level -> The Mediaserver will be in manual mode, user must take care of starting stopping the server. Configuration
 *    can be either automatically or manually. See below -Configuration-
 * 3) Method level -> The Mediaserver will be be started/stopped and configured automatically.
 * 
 * If the annotation is defined in more than one Fields, then we will create a new instance for each field.
 * 
 * - Configuration -
 * Using the @Mediaserver annotation, we can define the endpoints we want to configure for the server. 
 * For example @Mediaserver(IVR=2, CONF=5, RELAY=15).
 * The default values for the number of endoints are : IVR = 2, CONF = 2, RELAY = 2.
 * 
 * In case of Class and Method level definition, server configuration must be provided in the annotation itself, while when we 
 * define the annotation at the Field level, we can provide the ConfMode=MediaserverConfMode.MANUAL flag to indicate that
 * we will manualy configure the server. 
 * 
 * Annotation must be defined only once at one place, either it be Class or Field or Method level.
 *
 * @author <a href="mailto:gvagenas@gmail.com">George Vagenas</a>
 */

public class MediaServerProducer {

	private Logger logger = Logger.getLogger(MediaServerProducer.class);

	private Boolean isMediaserverAnnotationPresentField = false;
	private Boolean isMediaserverAnnotationPresentClass = false;
	private Boolean isMediaserverAnnotationPresentMethod = false;
	private List<Field> deployableServerFields = new ArrayList<Field>();
	private List<Method> deployableServerMethods = new ArrayList<Method>();
	private Object testInstance;
	//This instance will be used for Class and Method level annotations
	private EmbeddedMediaserver embeddedMediaserver;
	//This list will be used when annotation is present at the Field level
	private List<EmbeddedMediaserver> embeddedMediaserverList = new ArrayList<EmbeddedMediaserver>();

	public MediaServerProducer() {
		embeddedMediaserver = new EmbeddedMediaserverImpl();
	}

	/*
	 * Use @BeforeClass event in order to scan the test class for annotation we might be interesting.
	 * Event fired Before the Class execution.
	 */
	public void executeBeforeClass(@Observes BeforeClass event, TestClass testClass) throws Exception{
		testClass = event.getTestClass();

		if(!embeddedMediaserver.isStarted()) {

			//1. Check whether the annotation is present at the class level
			if (testClass.isAnnotationPresent(Mediaserver.class)) {
				isMediaserverAnnotationPresentClass = true;
			}

			//2. Check whether the annotation is present at one of the fields
			Field[] fields = testClass.getJavaClass().getDeclaredFields();
			for (Field field : fields) {
				if (field.isAnnotationPresent(Mediaserver.class)){
					if (field.getType().isAssignableFrom(EmbeddedMediaserver.class)){
						isMediaserverAnnotationPresentField = true;
						deployableServerFields.add(field);
					}
				}
			}
			//3. Check whether the annotation is present at the test method level
			Method[] methods = testClass.getMethods(Mediaserver.class); 
			//					((Object)testClass).getClass().getMethods();
			for (Method method : methods) {
				if (method.isAnnotationPresent(Mediaserver.class)){
					isMediaserverAnnotationPresentMethod = true;
					deployableServerMethods.add(method);
				}
			}

			//Mediaserver annotation must be defined in only one place
			if (isMediaserverAnnotationPresentField && (isMediaserverAnnotationPresentClass || isMediaserverAnnotationPresentMethod)
					|| (isMediaserverAnnotationPresentClass && isMediaserverAnnotationPresentMethod)){
				throw new EmbeddedMediaserverException("MediaServer annotation specified many times!");
			}

			//If the annotation is defined in the Class level, the start the server
			if (isMediaserverAnnotationPresentClass)
				startAndConfigureServer(testClass.getAnnotation(Mediaserver.class));

		}


	}

	// Event fired before the execution of a test method
	public void executeBeforeTestMethod(@Observes Before event, TestClass testClass) throws Exception
	{
		//If the annotation is present at the field level, we just assign the server to the field and
		//we leave the user to take control of the server (start/stop/configure etc)
		//Otherwise we have to start/stop/configure the server accordingly

		if (!embeddedMediaserver.isStarted()) {
			testInstance = event.getTestInstance();
			if (isMediaserverAnnotationPresentField) {
				setMediaserver(testInstance, deployableServerFields);
			}
			if (isMediaserverAnnotationPresentClass) {
				//No need to do nothing on the Class level. The server has already 
				//been started at the @Observes BeforeClass event
			}
			//Start and configure the server when is defined at the Method level
			if (isMediaserverAnnotationPresentMethod) {
				if (deployableServerMethods.contains(event.getTestMethod())) {
					startAndConfigureServer(event.getTestMethod().getAnnotation(Mediaserver.class));
				}

			}
		}
	}

	public void executeAfterClass(@Observes AfterClass event, TestClass testClass) {

		//When we have the annotation at the Class level, we need to stop the server
		//at the event @Observes AfterClass

		if (embeddedMediaserver.isStarted()){
			if (isMediaserverAnnotationPresentClass) {
				embeddedMediaserver.stopServer();
			} 
		}
	}

	public void executeAfterTestMethod(@Observes After event, TestClass testClass) {

		//When we have the annotation at the Test Method level, we need to stop the server
		//at the event @Observes After (when the test method finish)

		if (embeddedMediaserver.isStarted()){
			if (isMediaserverAnnotationPresentMethod) {
				embeddedMediaserver.stopServer();
			}
		}
	}

	private void setMediaserver(Object testInstance, List<Field> fields) throws IllegalArgumentException, IllegalAccessException
	{
		for (Field field : fields) {
			EmbeddedMediaserver embeddedMediaserver = new EmbeddedMediaserverImpl();
			embeddedMediaserver.setId("Embedded Mediaserver for Field "+field.getName());
			embeddedMediaserverList.add(embeddedMediaserver);
			Boolean flag = field.isAccessible();
			field.setAccessible(true);
			//Throws IllegalArgumentException cause we don't have access to the TestClass instance
			EmbeddedMediaserver fieldValue = (EmbeddedMediaserver) field.get(testInstance);
			if (fieldValue == null){
				// If value is null then set it to the appropriate Embedded Container. 
				field.set(testInstance, embeddedMediaserver);
			} else {
				//if value is not null then set it to NULL. 
				field.set(testInstance, null);
			}
			field.setAccessible(flag);
			try {
				startAndConfigureServer(field.getAnnotation(Mediaserver.class));
			} catch (Exception e) {
				e.printStackTrace();
			}

		}
	}

	private void startAndConfigureServer(Mediaserver anno) throws Exception {

		if (anno.ConfMode() == MediaserverConfMode.MANUAL){
			if (isMediaserverAnnotationPresentField) { 
				logger.info("Mediaserver defined in a Field level annotation, in manual mode. Skipping configuration");
				return;
			} else {
				throw new EmbeddedMediaserverException("Mediaserver in MANUAL mode is allowed only in Feild level annotation!");
			}
		}

		//Start and configure server when annotation is defined at the Class or Method level
		if (!isMediaserverAnnotationPresentField) {
			embeddedMediaserver.startServer();

			embeddedMediaserver.setId(anno.ID());
			embeddedMediaserver.installEndpoint(EndpointType.IVR, anno.IVR());
			embeddedMediaserver.installEndpoint(EndpointType.CONFERENCE, anno.CONF());
			embeddedMediaserver.installEndpoint(EndpointType.PACKETRELAY, anno.RELAY());
		}
		//Start and configure server when annotation is defined at the Field level
		else {
			for(EmbeddedMediaserver embeddedMediaserver: embeddedMediaserverList) {
				embeddedMediaserver.startServer();

				embeddedMediaserver.setId(embeddedMediaserver.getId()+"_"+anno.ID());
				embeddedMediaserver.installEndpoint(EndpointType.IVR, anno.IVR());
				embeddedMediaserver.installEndpoint(EndpointType.CONFERENCE, anno.CONF());
				embeddedMediaserver.installEndpoint(EndpointType.PACKETRELAY, anno.RELAY());

			}
		}


	}

}