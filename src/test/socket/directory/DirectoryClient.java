package test.socket.directory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import util.XMLUtility;

public class DirectoryClient {
	public static void main(String[] args) {

		// Set the host name , for a remote computer give the IP address
		final String hostName = "127.0.0.1";
		// Set the port number more than 1023
		final int portNumber = 9999;
		DirectoryClient client = new DirectoryClient();
		client.connectServer(hostName, portNumber);
		//client.processUserCommand("list");
		//<request><header><type>list</type></header><body/></request>
		//client.parseResponse("<response><header><type>list</type></header><body><services><service><serviceName>test.SampleClass</serviceName><methods><method><methodName>addNumbers</methodName><returnType>double</returnType><parameters><parameterType>double</parameterType><parameterType>double</parameterType></parameters></method><method><methodName>subtract</methodName><returnType>double</returnType><parameters><parameterType>double</parameterType><parameterType>double</parameterType></parameters></method></methods></service></services></body></response>");
		//<request><header><type>invoke</type></header><body><service><serviceName>test.SampleClass</serviceName><Method><methodName>addNumbers</methodName><parameters><parameter>50</parameter><parameter>40</parameter></parameters></Method></service></body></request>
		//client.parseResponse("<response><header><type>result</type></header><body><message>90.0</message></body></response>");
	}

	/*
	 * Tries to connect to the server with the host name and port Number
	 */
	private void connectServer(String hostName, int portNumber) {
		Socket serviceDirsocket = null;
		PrintWriter out = null;
		BufferedReader in = null;

		try {
			serviceDirsocket = new Socket(hostName, portNumber);
			out = new PrintWriter(serviceDirsocket.getOutputStream(), true);
			in = new BufferedReader(new InputStreamReader(
					serviceDirsocket.getInputStream()));
			System.out.println("Connected to Directory Server running on "
					+ hostName + " at port Number " + portNumber);
		} catch (UnknownHostException e) {
			System.err.println("Don't know about host: " + hostName);
		} catch (IOException e) {
			System.err.println("Couldn't get I/O for the connection to: "
					+ hostName);
		}

		try {

			BufferedReader stdIn = new BufferedReader(new InputStreamReader(
					System.in));
			String userInput;

			// Prompt at the standard input asking user to enter command.
			System.out.print("input: ");
			// keep reading the console
			while ((userInput = stdIn.readLine()) != null) {
				//Takes the command line arguments from the user console and converts them to XML request
				Document requestDoc = processUserCommand(userInput);
				//If XML document is ready sent it to server
				if (requestDoc != null) {
					System.out.println("Sending data to server ... "
							+ XMLUtility.xmlToString(requestDoc));
					out.println(XMLUtility.xmlToString(requestDoc));
				} else {
					System.out.print("Enter A valid command\n");
					continue;
				}
				//Quit the client 
				if (userInput.equalsIgnoreCase("bye")) {
					break;
				}
				// Read the server output
				String responseLine = in.readLine();
				// System.out.println("Result: " + responseLine);
				// Take action after getting the server response
				parseResponse(responseLine);
			}
			out.close();
			in.close();
			serviceDirsocket.close();
			System.out.println("Connection Closed ... ");
		} catch (UnknownHostException e) {
			System.err.println("Trying to connect to unknown host: " + e);
		} catch (IOException e) {
			System.err.println("IOException:  " + e);
		}

	}

	/*
	 * This takes the user entered string and converts into the XML request Document
	 */
	private Document processUserCommand(String userInput) {
		String[] arguments = userInput.split("\\s+");
		String command = arguments[0];
		Document requestXML = null;
		//for directory listing request 
		if (command.equalsIgnoreCase("list")) {
			requestXML = generateRequestXML("list");
		} else if (command.equalsIgnoreCase("invoke")) {
			//Argument 1 : service name
			//Argument 2 : method name
			//Argument 3 onwards : parameters  
			String serviceName = arguments[1];
			String methodName = arguments[2];
			ArrayList<String> parameters = new ArrayList<String>();
			for (int i = 3; i < arguments.length; i++) {
				parameters.add(arguments[i]);
			}
			requestXML = generateRequestXML("invoke", serviceName, methodName,
					parameters);

		} else {
			requestXML = null;
		}
		return requestXML;
	}

	/*
	 * Generates the request XML for the invoke method
	 */
	private Document generateRequestXML(String type, String serviceName,
			String methodName, ArrayList<String> parameters) {

		Document HeaderDoc = generateRequestXML(type);

		Element serviceNode = HeaderDoc.createElement("service");
		Element serviceNameNode = HeaderDoc.createElement("serviceName");
		Text serviceNameText = HeaderDoc.createTextNode(serviceName);
		serviceNameNode.appendChild(serviceNameText);
		serviceNode.appendChild(serviceNameNode);

		Element methodNode = HeaderDoc.createElement("Method");
		Element methodNameNode = HeaderDoc.createElement("methodName");
		Text methodNameText = HeaderDoc.createTextNode(methodName);
		methodNameNode.appendChild(methodNameText);
		methodNode.appendChild(methodNameNode);
		Element ParametersNode = HeaderDoc.createElement("parameters");
		methodNode.appendChild(ParametersNode);
		for (String parameterType : parameters) {
			Element parameterTypeNode = HeaderDoc.createElement("parameter");
			Text parameterTypeText = HeaderDoc.createTextNode(parameterType);
			parameterTypeNode.appendChild(parameterTypeText);
			ParametersNode.appendChild(parameterTypeNode);
		}
		Node bodyNode = HeaderDoc.getElementsByTagName("body").item(0);
		serviceNode.appendChild(methodNode);
		bodyNode.appendChild(serviceNode);

		return HeaderDoc;
	}

	/*
	 * Generates the request XML for the discovery service
	 */
	private Document generateRequestXML(String command) {
		Document doc = XMLUtility.createDocument();
		Element root = doc.createElement("request");
		doc.appendChild(root);
		Element header = doc.createElement("header");
		root.appendChild(header);
		Element type = doc.createElement("type");
		Text typeText = doc.createTextNode(command);
		type.appendChild(typeText);
		header.appendChild(type);
		Element body = doc.createElement("body");
		root.appendChild(body);

		return doc;

	}

	/*
	 * This method takes action after getting response from the server
	 */
	private void parseResponse(String responseLine) {
		Document responseDoc = XMLUtility.StringToXML(responseLine);
		// Determine the type of response
		String responsetype = responseDoc.getElementsByTagName("type").item(0)
				.getTextContent();
		System.out.println("Response Type : " + responsetype);
		Document body = unWrap(responseDoc);
		if (responsetype.equalsIgnoreCase("list")) {
			HashMap<String, ArrayList<test.socket.directory.Method>> serviceModel = getServices(body);
			// Display the list of services , methods and their signature
			displayMenu(serviceModel);
		} else if (responsetype.equalsIgnoreCase("result")) {
			System.out.println(getResult(body));
		} else {
			System.out.println("Unknown Response");
		}
	}

	/*
	 * Parsing the  the result or error message from the envolope body 
	 */
	private String getResult(Document body) {
		String message = body.getElementsByTagName("message").item(0)
				.getTextContent();
		return message;
	}

	/*
	 * Displays the list of services and methods along with their parameters
	 */
	private void displayMenu(HashMap<String, ArrayList<Method>> serviceModel) {
		for (String service : serviceModel.keySet()) {
			System.out.println(service);
			for (Method method : serviceModel.get(service)) {
				System.out.print(":: " + method.getReturnType() + " "
						+ method.getMethodName());
				System.out.print("(");
				for (String paramType : method.getParameters()) {
					System.out.print(" " + paramType + " ");
				}
				System.out.print(")\n");
			}
		}
	}

	/*
	 * This strips the actual output of the server from the response envelope
	 */
	private Document unWrap(Document responseDoc) {
		Document doc = XMLUtility.createDocument();
		doc.appendChild(doc.importNode(responseDoc.getDocumentElement(), true));
		return doc;
	}

	
	/*
	 * Builds the in memory model of the service list from the XML response
	 */
	private HashMap<String, ArrayList<Method>> getServices(Document responseDoc) {

		HashMap<String, ArrayList<Method>> serviceMap = new HashMap<String, ArrayList<Method>>();
		NodeList nodeList = responseDoc.getElementsByTagName("service");

		for (int serviceCounter = 0; serviceCounter < nodeList.getLength(); serviceCounter++) {
			Node serviceNode = nodeList.item(serviceCounter);
			if (serviceNode.getNodeType() == Node.ELEMENT_NODE) {
				Element serviceElement = (Element) serviceNode;
				NodeList serviceNameNode = serviceElement
						.getElementsByTagName("serviceName");
				String serviceName = serviceNameNode.item(0).getTextContent();
				NodeList methodsNodeList = serviceElement
						.getElementsByTagName("method");
				ArrayList<Method> methods = new ArrayList<Method>();

				for (int methodCounter = 0; methodCounter < methodsNodeList
						.getLength(); methodCounter++) {
					Method method = new Method();
					Node methodNode = methodsNodeList.item(methodCounter);
					String methodName = ((Element) methodNode)
							.getElementsByTagName("methodName").item(0)
							.getTextContent();
					method.setMethodName(methodName);
					String returnType = ((Element) methodNode)
							.getElementsByTagName("returnType").item(0)
							.getTextContent();
					method.setReturnType(returnType);
					NodeList parameterNodeList = ((Element) methodNode)
							.getElementsByTagName("parameterType");
					ArrayList<String> parameters = new ArrayList<String>();
					for (int parameterCounter = 0; parameterCounter < parameterNodeList
							.getLength(); parameterCounter++) {
						Node parameterNode = parameterNodeList
								.item(parameterCounter);
						String parameterType = ((Element) parameterNode)
								.getTextContent();
						parameters.add(parameterType);
					}
					method.setParameters(parameters);
					methods.add(method);
				}
				serviceMap.put(serviceName, methods);
			}
		}
		return serviceMap;
	}
}