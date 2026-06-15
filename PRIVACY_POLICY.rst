.. SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0

===========================
AppManagerNG Privacy Policy
===========================
(DRAFT REVISION NO. 4)

1. Definition
=============

- "The Project" refers to AppManagerNG, including the git repository, issue
  tracker, release artifacts, and maintainer-operated project spaces.
- "We", "Us", and similar capitalized pronouns refer to the maintainers of
  The Project.
- "E-Mail" or "E-Mails" refers to messages sent directly to the maintainers of
  The Project.
- "The Software" refers to AppManagerNG software distributed by its maintainers.
- "The Project Hosting Providers" refers to GitHub and any future official
  project hosting service linked from the repository.
- "Third-party Services" refers to The Project Hosting Providers along with
  VirusTotal, Pithus, Obtainium, F-Droid, IzzyOnDroid, and any future hosted
  translation service linked from the repository.
- "Third-party Websites" refers to websites We do not control or operate.
- "You", "Yours", and similar capitalized pronouns refer to anyone who uses
  The Software or has contributed to The Project in any capacity.
- "PII" refers to personally identifiable information.

2. Information Collected from You
=================================

2.1. The Project
----------------
We DO NOT collect any information that You have not provided voluntarily.
The sources of this information include your contributions on GitHub, issue
comments, pull requests, discussions, and any E-Mails you send. This information
may include PII, such as Your real name and E-Mail address. If You send crash
reports and logs, they may also contain non-PII details such as Your device
name, operating system version, software version, language, and more.

The official project page is the GitHub repository:
https://github.com/SysAdminDoc/AppManagerNG. GitHub may collect traffic,
account, and interaction data under GitHub's own privacy policy.

2.2. The Software
-----------------
We DO NOT collect any information from the default ``floss`` build of The
Software.

The optional ``full`` build contains network-capable features. These features
are disabled until You opt in from Settings. When enabled:

- VirusTotal lookups may upload APK files or file hashes to VirusTotal.
- Pithus lookups may open or request reports from Pithus.
- Debloat-definition and tracker-database update checks may fetch pinned files
  from GitHub-hosted raw content. These checks do not intentionally send your
  installed package list or device identifiers.

Local networking used for ADB-over-TCP, wireless ADB pairing, and the local
privileged server is part of the app's device-control workflow and is not an
analytics or telemetry channel.

2.3. Third-party Services
-------------------------
Depending on the services used, the privacy policy of the following services
will apply to You:

- `GitHub`_ (stars, issues, pull requests, discussions, releases, traffic,
  raw-content hosting)
- `VirusTotal`_ (malware reports, file uploads, traffic)
- `Pithus`_ (malware reports, APK analysis pages, traffic)
- `Obtainium`_ (release-update tracking, if You use Obtainium)
- `F-Droid`_ and `IzzyOnDroid`_ (repository metadata and app-store traffic, if
  AppManagerNG is distributed through those channels)
- A future hosted translation service, if one is linked from the repository.

2.4. Third-party Websites
-------------------------
Links to Third-party Websites are provided for your benefit. For your safety,
it is recommended that You read and understand the privacy policy of these
websites before visiting them.

3. Data Retention Policy
========================
Information collected through Git and GitHub is stored indefinitely and may be
accessible to anyone, anywhere. E-Mails that do not have legal significance can
be retained for a maximum of one year. E-Mails deemed legally significant can be
kept permanently, both online and offline. To understand the data retention
policies of the Third-party Services and Third-party Websites, please refer to
their respective privacy policies.

4. Removal of Information
=========================
You can request the removal of PII by either sending Us an E-Mail or creating
an issue. You can also request the removal of non-PII, but please note that the
removal is not guaranteed. In both cases, the following types of information
cannot be removed by Us:

- Information present in a commit message, such as the ``Signed-off-by:`` tag
- Information contained in a file, since it is part of git history
- Forked repositories, which must be handled by the person who forked the
  repository
- Reactions to GitHub issues, comments, and discussions, which You must remove
  Yourself
- Information stored by Third-party Services or Third-party Websites, which You
  must ask them to remove

5. Changes to the Privacy Policy
================================
All changes, except those related to spelling or grammar, will be announced on
official project channels. Unless stated otherwise, the updated privacy policy
will apply only to The Software released after the changes.

6. Project Maintainers
======================
1. **Name:** SysAdminDoc

   **Contact:** https://github.com/SysAdminDoc/AppManagerNG/issues

.. _GitHub: https://docs.github.com/en/site-policy/privacy-policies/github-privacy-statement
.. _VirusTotal: https://support.virustotal.com/hc/en-us/articles/115002168385-Privacy-Policy
.. _Pithus: https://beta.pithus.org/about
.. _Obtainium: https://github.com/ImranR98/Obtainium
.. _F-Droid: https://f-droid.org/en/about/#terms-etc
.. _IzzyOnDroid: https://apt.izzysoft.de/fdroid/
