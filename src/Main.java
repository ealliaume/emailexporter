import com.google.common.collect.Sets;
import com.sun.mail.imap.IMAPBodyPart;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.net.QCodec;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

import javax.mail.Header;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMultipart;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Scanner;
import java.util.Set;

import static org.apache.commons.lang.StringUtils.*;

public class Main {
	public static void main(String... args) throws IOException {
		Scanner scanner = new Scanner(System.in);
		System.out.println("Please input your login:");
		String login = scanner.nextLine();
		System.out.println("Please input your password (BEWARE: clear text):");
		String password = scanner.nextLine();

		try (ImapEmailSource source = ImapEmailSource
			.configure()
			.withProvider(ImapImportConfig.Providers.GMAIL)
			.withLogin(login)
			.withPassword(password)
			.withImapFolder("INBOX").get()) {

			File outputFolder = new File("/tmp/emails");
			FileUtils.forceMkdir(outputFolder);

			for (Message message : source) {
				String folderName = message.getFolder().getName();
				try {
					File messageFolder = new File(new File(outputFolder, folderName), "message-"+message.getMessageNumber());

					Set<String> stringHeaders = Sets.newHashSet();
					Enumeration headers = message.getAllHeaders();
					while (headers.hasMoreElements()) {
						Header header = (Header)headers.nextElement();
						stringHeaders.add(header.getName()+":"+header.getValue());
					}
					FileUtils.writeLines(new File(messageFolder, "headers.txt"), stringHeaders);

					String classNameForContent = message.getContent().getClass().getName();
					switch (classNameForContent) {
						case "java.lang.String":
							FileUtils.writeStringToFile(new File(messageFolder, "inline.txt"), (String)message.getContent());
							break;
						case "javax.mail.internet.MimeMultipart":
							MimeMultipart multipart = (MimeMultipart)message.getContent();
							extractPartData(new File(messageFolder, "multipart"), multipart);
							break;
						default:
							System.out.println("You're fucked, no handler for content class "+classNameForContent);
							break;
					}
				} catch (IOException | MessagingException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private static void extractPartData(File parentFolder, MimeMultipart multipart) throws MessagingException, IOException {
		FileUtils.forceMkdir(parentFolder);

		for (int i = 0; i < multipart.getCount(); i++) {
			IMAPBodyPart part = (IMAPBodyPart) multipart.getBodyPart(i);

			String type = part.getContentType().toLowerCase();
			if (type.toLowerCase().startsWith("text/plain")) {
				FileUtils.writeStringToFile(new File(parentFolder, "part-" + i + ".txt"), (String) part
					.getContent());
			} else if (type.toLowerCase().startsWith("text/html")) {
				FileUtils.writeStringToFile(new File(parentFolder, "part-" + i + ".html"), (String)part.getContent());
			} else if (part.getContent() instanceof MimeMultipart) {
				File partFolder = new File(parentFolder, "part-" + i);
				extractPartData(partFolder, (MimeMultipart)part.getContent());
			} else if(part.getContent() instanceof InputStream) {
				IOUtils.copy((InputStream) part.getContent(), new FileOutputStream(new File(parentFolder, fileNameFromContentTypeHeader(type))));
			} else if(part.getContent() instanceof String) {
				FileUtils.writeStringToFile(new File(parentFolder, fileNameFromContentTypeHeader(type)), (String)part.getContent());
			} else {
				System.out.println("You're fucked, no handler for IMAP part " + part.getContent()
					.getClass()
					.getName() + " on content-type " + type);
			}
		}
	}

	public static String fileNameFromContentTypeHeader(String contentType) {
		if (StringUtils.containsIgnoreCase(contentType, "name=")) {
			if (StringUtils.containsIgnoreCase(contentType, "name=\"") && StringUtils.contains(contentType, "?q?")) {
				// encoded
				String encoded = substringBefore(substringAfter(contentType, "name=\""), "\"");

				String[] parts = split(encoded, "\n ");
				StringBuilder result = new StringBuilder();
				for (String part : parts) {
					if (isNotBlank(part)) {
						try {
							result.append(new QCodec().decode(part));
						} catch (DecoderException e) {
							e.printStackTrace();
						}
					}
				}
				return result.toString();
			} else {
				// plain
				String plain = substringBefore(substringAfter(contentType, "name="), ";");
				if (plain.startsWith("\"")) {
					plain = plain.substring(1);
				}
				if (plain.endsWith("\"")) {
					plain = plain.substring(0, plain.length() - 1);
				}
				return plain;
			}
		}
		if (containsIgnoreCase(contentType, "text/calendar")) {
			return "invite.ics";
		}

		System.out.println("Warning: Could not decode a filename from '"+contentType+"'");
		return "invalidFileName";
	}
}
