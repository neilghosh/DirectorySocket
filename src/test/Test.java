package test;

import java.util.Scanner;
// import additional packages
import java.io.*;

// import DOM related classes
import org.w3c.dom.*;

import util.XMLUtility;

import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;

public class Test {
	public static void main(String[] args) {

		System.out.println(XMLUtility.formatXML("<root></root>"));
	
	}
}
