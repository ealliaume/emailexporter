import org.junit.Test;

public class MainTest {
	@Test
	public void shouldDecodeFilenames() {
		String input = "application/msword; \n"
		+ "\tname=\"=?iso-8859-1?q?doc=5f26-sa-rapport=5fde=5fgestion=5faffect?=\n"
		+ " =?iso-8859-1?q?ation=5fdes=5fr=e9sultats-approbation=5fdes?=\n"
		+ " =?iso-8859-1?q?=5fcomptes-mandataires=5fsociaux-action?=\n"
		+ " =?iso-8859-1?q?nariat-salari=e9-administrateurs=5f.doc?=\"";

		System.out.println(Main.fileNameFromContentTypeHeader(input));
	}
}
