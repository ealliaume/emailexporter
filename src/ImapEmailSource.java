import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;

import javax.mail.*;
import java.io.Closeable;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Stack;

public class ImapEmailSource implements Iterable<Message>, AutoCloseable {
	private List<Closeable> openedIterators = Lists.newArrayList();

	final String login;
	final String password;
	final String imapFolder;
	final Integer maxEmails;
	final ImapImportConfig.Provider provider;
	private final Function<String, Integer> guessStartingPoint;

	private ImapEmailSource(String login, String password, String imapFolder, Integer maxEmails, ImapImportConfig.Provider provider, Function<String, Integer> guessStartingPoint) {
		this.login = login;
		this.password = password;
		this.imapFolder = imapFolder;
		this.maxEmails = maxEmails;
		this.provider = provider;
		this.guessStartingPoint = guessStartingPoint;
	}

	public Integer getMaxEmails() {
		return maxEmails;
	}

	public Function<String, Integer> getGuessStartingPoint() {
		return guessStartingPoint;
	}

	public static EmailInfoConfigBuilder configure() {
		return new EmailInfoConfigBuilder();
	}

	@Override
	public Iterator<Message> iterator() {
		EmailIterator emailIterator = EmailIterator.from(this);
		openedIterators.add(emailIterator);
		return emailIterator;
	}

	@Override
	public void close() {
		Exception lastException = null;
		for (Closeable closeable : openedIterators) {
			try {
				closeable.close();
			} catch (Exception e) {
				lastException = e;
			}
		}
		if (lastException != null) {
			throw Throwables.propagate(lastException);
		}
	}

	public static class EmailInfoConfigBuilder {
		private String login;
		private String password;
		private String imapFolder;
		private Integer maxEmails = Integer.MAX_VALUE;
		private ImapImportConfig.Provider provider;
		private Function<String, Integer> guessStartingPoint;

		public EmailInfoConfigBuilder withLogin(String login) {
			this.login = login;
			return this;
		}

		public EmailInfoConfigBuilder withPassword(String password) {
			this.password = password;
			return this;
		}

		public EmailInfoConfigBuilder withImapFolder(String imapFolder) {
			this.imapFolder = imapFolder;
			return this;
		}

		public EmailInfoConfigBuilder withProvider(ImapImportConfig.Provider provider) {
			this.provider = provider;
			return this;
		}

		public EmailInfoConfigBuilder withStartingPointFunction(Function<String, Integer> guessStartingPoint) {
			this.guessStartingPoint = guessStartingPoint;
			return this;
		}

		public ImapEmailSource get() {
			Preconditions.checkNotNull(login, "Login should not be null");
			Preconditions.checkNotNull(password, "Password should not be null");

			return new ImapEmailSource(login, password, getImapFolder(), maxEmails, provider, guessStartingPoint);
		}

		private String getImapFolder() {
			if (StringUtils.isEmpty(imapFolder)) {
				imapFolder = "INBOX";
			}
			return imapFolder;
		}
	}

	private static class EmailIterator extends AbstractIterator<Message> implements Closeable {
		private KnownFolder folder;
		private final Store mailStore;
		private int currentMessageId;
		private final int maxEmail;
		private boolean failed;
		private int folderMessageCount;
		private final Long start;
		private final Stack<KnownFolder> remainingFolders;
		private final Function<String, Integer> guessStartingPoint;

		public static EmailIterator from(ImapEmailSource config) {
			return new EmailIterator(config);
		}

		private EmailIterator(ImapEmailSource config) {
			remainingFolders = new Stack<>();
			guessStartingPoint = config.getGuessStartingPoint();

			start = System.currentTimeMillis();

			try {
				Properties properties = new Properties();
				if (config.provider.isSecure()) {
					properties.setProperty("mail.store.protocol", "imaps");
					properties.setProperty("mail.imaps.partialfetch", "false");
				} else {
					properties.setProperty("mail.store.protocol", "imap");
					properties.setProperty("mail.imap.partialfetch", "false");
				}
				properties.setProperty("mail.mime.base64.ignoreerrors", "false"); // Set to true to try recovery of partial content emails (i.e. when an error occurred at transmission)

				Session mailSession = Session.getDefaultInstance(properties);

				if (config.provider.isSecure()) {
					mailStore = mailSession.getStore("imaps");
				} else {
					mailStore = mailSession.getStore("imap");
				}

				mailStore.connect(config.provider.getHost(), config.provider.getPort(), config.login, config.password);

				displayFolders(mailStore.getDefaultFolder(), "", remainingFolders);

				failed = false;
				maxEmail = config.getMaxEmails();
				folderMessageCount = -1;
			} catch (Exception e) {
				throw new RuntimeException("Could not connect a gmail imaps session", e);
			} finally {
				System.out.println("Opened folder in "+(System.currentTimeMillis() - start)/1000+"s.");
			}
		}

		private void displayFolders(Folder rootFolder, String prefix, Stack<KnownFolder> folders) throws MessagingException {
			for (Folder folder : rootFolder.list()) {
				folders.push(new KnownFolder(folder, guessStartingPoint));
				displayFolders(folder, prefix + rootFolder.getName() + ">", folders);
			}
		}

		@Override
		protected Message computeNext() {
			try {
				if (folder == null) {
					// start on new folder
					folder = remainingFolders.pop();
					folder.getFolder().open(Folder.READ_ONLY);

					folderMessageCount = folder.getFolder().getMessageCount();
					currentMessageId = folder.getStartAt();

					System.out.println("Now working on "+folder.getFolder().getName() + " with "+folderMessageCount);
				}

				if (currentMessageId >= maxEmail) {
					return _endOfData();
				}

				if (failed) {
					return _endOfData();
				}

				if (currentMessageId > folderMessageCount) {
					folder.getFolder().close(false);
					if (remainingFolders.size() > 0) {
						folder = null;

						return computeNext();
					}
					return _endOfData();
				}

				if (currentMessageId % 10 == 0) {
					System.out.println("Downloading emails from "+folder.getFolder().getName()+", " +(currentMessageId * 100)/ folderMessageCount +"% done.");
				}

				return folder.getFolder().getMessage(currentMessageId++);
			} catch (MessagingException e) {
				failed = true;
				throw new RuntimeException("Can't read messages", e);
			}
		}

		private Message _endOfData() {
			long timeInSeconds = (System.currentTimeMillis() - start) / 1000;
			System.out.println("Done downloading emails in "+ timeInSeconds +"s at rate of "+(folderMessageCount/timeInSeconds)+" messages per second.");
			return endOfData();
		}

		@Override
		public void close() {
			Exception lastException = null;
			try {
				folder.getFolder().close(false);
			} catch (MessagingException e) {
				lastException = new RuntimeException("Can't close message box", e);
			}
			try {
				mailStore.close();
			} catch (MessagingException e) {
				lastException = new RuntimeException("Can't close mail store", e);
			}
			if (lastException != null) {
				throw Throwables.propagate(lastException);
			}
		}
	}

	private static class KnownFolder {
		private final Folder folder;
		private final int startAt;

		private KnownFolder(Folder folder, Function<String, Integer> guessStartPoint) {
			this(folder, guessStartPoint.apply(folder.getName()));
		}

		private KnownFolder(Folder folder, int startAt) {
			this.folder = folder;
			this.startAt = startAt;
		}

		public Folder getFolder() {
			return folder;
		}

		public int getStartAt() {
			return startAt;
		}
	}
}
