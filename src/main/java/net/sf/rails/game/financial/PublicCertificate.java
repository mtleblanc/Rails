package net.sf.rails.game.financial;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedSet;

import net.sf.rails.common.LocalText;
import net.sf.rails.game.*;
import net.sf.rails.game.model.CertificatesModel;
import net.sf.rails.game.state.IntegerState;
import net.sf.rails.game.state.Ownable;
import net.sf.rails.game.state.Owner;
import net.sf.rails.game.state.Typable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableSortedSet;


/**
 * Rails 2.x TODO:
 * Create an abstraction that defines a CertificateType (currently attributes president, shares, certificateCount)
 * Each certificate belongs to a Certificate Type and a Company
 * Then clean up the current mess, including the static map, which is only for backward compatibility for early 1.x versions
 */

public class PublicCertificate extends RailsOwnableItem<PublicCertificate>
        implements Certificate, Cloneable, Typable<PublicCompany> {

    /**
     * Combination defines a set of certificates
     */
    public static class Combination implements Comparable<Combination>, Iterable<PublicCertificate> {

        private final SortedSet<PublicCertificate> certs;

        private Combination(SortedSet<PublicCertificate> certs) {
            this.certs = certs;
        }

        public static Combination create(Iterable<PublicCertificate> certs) {
            return new Combination(ImmutableSortedSet.copyOf(certs));
        }

        public SortedSet<PublicCertificate> getCertificates() {
            return certs;
        }

        public int size() {
            return certs.size();
        }

        @Override
        public int compareTo(Combination other) {
            return Integer.compare(certs.size(), other.size());
        }

        @Override
        public Iterator<PublicCertificate> iterator() {
            return certs.iterator();
        }

        public String toString() {
            return certs.toString();
        }

    }

    /** From which public company is this a certificate */
    protected PublicCompany company;
    /**
     * Share percentage represented by this certificate
     */
    protected IntegerState shares = IntegerState.create(this, "shares");

    /** President's certificate? */
    protected boolean president;

    /** Count against certificate limits */
    protected float certificateCount = 1.0f;

    /** Certificate-level availability at the start of the game.
     *  Added by EV 2/2021:
     *  Normally, only available certificates can be bought in a Stock Round.
     *  Unavailable certs must always be picked up individually, usually
     *  to be exchanged against a single minor or private certificate.
     *
     *  This setting is now partly independent of the similar setting for the whole company.
     *  At setup, the company-level is checked first;
     *  - if true, then the available certs go to IPO, the unavailable to 'unavailable'.
     *  - if false, then all certificates go to 'unavailable'.
     *  In the latter case, the company must be explicitly made available ('released'),
     *  normally in a method overriding StockRound.gameSpecificChecks().
     *  At that time, only the available certificates got to IPO.
     * */
    protected boolean initiallyAvailable = true;

    /** A key identifying the certificate's unique ID */
    protected String certId;

    // FIMXE:
    /** Index within company (to be maintained in the IPO) */
    protected int indexInCompany;

    private static final Logger log = LoggerFactory.getLogger(PublicCertificate.class);

    // TODO: Rewrite constructors
    // TODO: Should every certificate have its own id and be registered with the parent?
    public PublicCertificate(RailsItem parent, String id, int shares, boolean president,
            boolean available, float certificateCount, int index) {
        super(parent, id, PublicCertificate.class);
        this.shares.set(shares);
        this.president = president;
        this.initiallyAvailable = available;
        this.certificateCount = certificateCount;
        this.indexInCompany = index;
    }

// TODO: Can be removed, as
//    most likely this does not work, as it duplicates ids
//    public PublicCertificate(PublicCertificate oldCert) {
//        super(oldCert.getParent(), oldCert.getId(), PublicCertificate.class);
//        this.shares = oldCert.getShares();
//        this.president = oldCert.isPresidentShare();
//        this.initiallyAvailable = oldCert.isInitiallyAvailable();
//        this.certificateCount = oldCert.getCertificateCount();
//        this.indexInCompany = oldCert.getIndexInCompany();
//    }

    @Override
    public RailsItem getParent(){
        return super.getParent();
    }

    @Override
    public RailsRoot getRoot() {
        return super.getRoot();
    }

    /** Set the certificate's unique ID, for use in deserializing */
    public void setUniqueId(String name, int index) {
        certId = name + "-" + index;
        getRoot().getCertificateManager().addCertificate(certId, this);
    }

    /** Set the certificate's unique ID */
    public String getUniqueId() {
        return certId;
    }

    public int getIndexInCompany() {
        return indexInCompany;
    }

    // FIXME: There is no guarantee that the parent of a certificate portfolio is a portfolioModel
    // Replace that by something that works
    public CertificatesModel getHolder() {
        //return getPortfolio().getParent().getShareModel(company);
        return null;
    }

    /**
     * @return if this is a president's share
     */
    public boolean isPresidentShare() {
        return president;
    }

    /**
     * Get the number of shares that this certificate represents.
     *
     * @return The number of shares.
     */
    public int getShares() {
        return shares.value();
    }

    /**
     * Get the percentage of ownership that this certificate represents. This is
     * equal to the number of shares * the share unit.
     *
     * @return The share percentage.
     */
    public int getShare() {
        return shares.value() * company.getShareUnit();
    }


    public void setInitiallyAvailable(boolean initiallyAvailable) {
        this.initiallyAvailable = initiallyAvailable;
    }

    public boolean isInitiallyAvailable() {
        return initiallyAvailable;
    }

    public void setPresident(boolean b) {
        president = b;
    }

    public PublicCompany getCompany() {
        return company;
    }

    public void setCompany(PublicCompany companyI) {
        company = companyI;
    }

    public String getTypeId() {
        String certTypeId = company.getId() + "_" + getShare() + "%";
        if (president) certTypeId += "_P";
        return certTypeId;
    }

    // Typable interface
    @Override
    public PublicCompany getType() {
        return company;
    }

    @Override
    protected Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            log.error("Cannot clone certificate:", e);
            return null;
        }
    }

    public PublicCertificate copy() {
        return (PublicCertificate) this.clone();
    }

    /**
     * Compare is based on
     * A) Presidency (presidency comes first in natural ordering)
     * B) Number of Shares (more shares means come first)
     * C) Id of CertificateType
     * D) Id of Certificate
     */
    // FIXME: The default comparator can only contain final attributes, otherwise
    // otherwise Portfolios (TreeMaps) might get confused
    // Implement another comparator for display that does not define a standard sorting
    @Override
    public int compareTo(Ownable other) {
        if (other instanceof PublicCertificate) {
            PublicCertificate otherCert = (PublicCertificate)other;
            // sort by the criteria defined above
            return ComparisonChain.start()
                    .compare(this.getCompany(), otherCert.getCompany())
//                    .compare(otherCert.isPresidentShare(), this.isPresidentShare())
//                    .compare(otherCert.getShares(), this.getShares())
//                    .compare(this.getType().getId(), otherCert.getType().getId())
                    .compare(this.getId(), otherCert.getId())
                    .result();
        } else {
            return super.compareTo(other);
        }
    }

    public void setShares(int numShares) {
       this.shares.set(numShares);

    }

    // Certificate Interface
    public float getCertificateCount() {
        return certificateCount;
    }

    @Deprecated
    public void setCertificateCount(float certificateCount) {
        this.certificateCount = certificateCount;
    }

    @Override
    public void moveTo(Owner newOwner) {
        super.moveTo (newOwner);

        // If this is a president certificate, also set the president
        if (president) {
            PublicCompany company = (PublicCompany) getParent();
            if (newOwner instanceof BankPortfolio) {
                company.setPresident(null);
            } else if (newOwner instanceof Player) {
                company.setPresident((Player)newOwner);
            }
        }
    }

        // Item interface
    /**
     * Get the name of a certificate. The name is derived from the company name
     * and the share percentage of this certificate. If it is a 100% share (as
     * occurs with e.g. 1835 minors), only the company name is given. If it is a
     * president's share, that fact is mentioned.
     */
    @Override
    public String toText() {
        int share = getShare();
        if (share == 100) {
            /* Applies to shareless minors: just name the company */
            return company.getId();
        } else if (president) {
            return LocalText.getText("PRES_CERT_NAME",
                    company.getId(),
                    getShare() );
        } else {
            return LocalText.getText("CERT_NAME",
                    company.getId(),
                    getShare());
        }
    }

    public String toString() {
        return getId();
    }

}
