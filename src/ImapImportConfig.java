import org.apache.commons.lang.StringUtils;

import static com.google.common.base.Preconditions.checkNotNull;

public class ImapImportConfig {
	private String login;
	private String password;
	private String imapFolder;
	private Long workspaceId;
	private String newWorkspaceName;
	private Integer maxEmail;
	private boolean importAttachments;
	private boolean importContent;
	private Provider provider;

	public String getLogin() {
		return login;
	}

	public void setLogin(String login) {
		this.login = login;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getImapFolder() {
		if (StringUtils.isEmpty(imapFolder)) {
			imapFolder = "INBOX";
		}
		return imapFolder;
	}

	public void setImapFolder(String imapFolder) {
		this.imapFolder = imapFolder;
	}

	public Long getWorkspaceId() {
		return workspaceId;
	}

	public void setWorkspaceId(Long workspaceId) {
		this.workspaceId = workspaceId;
	}

	public String getNewWorkspaceName() {
		return newWorkspaceName;
	}

	public void setNewWorkspaceName(String newWorkspaceName) {
		this.newWorkspaceName = newWorkspaceName;
	}

	public Integer getMaxEmail() {
		if (maxEmail == null) {
			maxEmail = Integer.MAX_VALUE;
		}
		return maxEmail;
	}

	public void setMaxEmail(Integer maxEmail) {
		this.maxEmail = maxEmail;
	}

	public boolean isImportAttachments() {
		return importAttachments;
	}

	public void setImportAttachments(boolean importAttachments) {
		this.importAttachments = importAttachments;
	}

	public boolean isImportContent() {
		return importContent;
	}

	public void setImportContent(boolean importContent) {
		this.importContent = importContent;
	}

	public Provider getProvider() {
		return provider;
	}

	public void setProvider(Provider provider) {
		this.provider = provider;
	}

	public interface Provider {
		String getHost();
		Integer getPort();
		Boolean isSecure();
	}

	public static ImapImportConfig.Provider customProvider(final String host, final Integer port, final Boolean secure) {
		checkNotNull(host);
		checkNotNull(port);
		checkNotNull(secure);

		return new ImapImportConfig.Provider() {
			@Override
			public String getHost() {
				return host;
			}

			@Override
			public Integer getPort() {
				return port;
			}

			@Override
			public Boolean isSecure() {
				return secure;
			}
		};
	}

	public enum Providers implements Provider {
		GMAIL("imap.gmail.com", 993, true);

		Providers(String host, Integer port, Boolean secure) {
			this.host = host;
			this.port = port;
			this.secure = secure;
		}

		private String host;
		private Integer port;
		private Boolean secure;

		public String getHost() {
			return host;
		}

		public Integer getPort() {
			return port;
		}

		public Boolean isSecure() {
			return secure;
		}
	}
}
