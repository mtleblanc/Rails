package net.sf.rails.game.specific._1837;

import java.util.ArrayList;
import java.util.List;

import net.sf.rails.game.*;
import net.sf.rails.game.financial.Bank;
import net.sf.rails.game.state.ArrayListMultimapState;
import net.sf.rails.game.state.ArrayListState;
import net.sf.rails.game.state.GenericState;
import net.sf.rails.game.state.Owner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rails.game.action.DiscardTrain;
import rails.game.action.FoldIntoNational;
import rails.game.action.PossibleAction;

import net.sf.rails.common.DisplayBuffer;
import net.sf.rails.common.LocalText;
import net.sf.rails.common.ReportBuffer;
import net.sf.rails.game.round.RoundFacade;

public class NationalFormationRound extends StockRound_1837 {
    private static final Logger log = LoggerFactory.getLogger(NationalFormationRound.class);

    public NationalFormationRound(GameManager parent, String id) {
        super(parent, id);
    }

    private PublicCompany_1837 national;
    private List<PublicCompany_1837> minors;
    private PublicCompany_1837 startingMinor;
    private boolean startNational;
    private boolean forcedStart;
    private boolean mergeNational;
    private boolean forcedMerge;

    private String nfrReportName;
    private boolean atNewPhase;

    private ArrayListState<Player> currentPlayerOrder; // To exchange minors
    private ArrayListMultimapState<Player, Company> minorsPerPlayer;
    //private ArrayListState<PublicCompany> closedMinors;

    protected enum Step {
            START,
            MERGE,
            DISCARD_TRAINS
        }

    private GenericState<Step> step;

    public static boolean nationalIsComplete(PublicCompany_1837 national) {

        for (PublicCompany company : national.getMinors()) {
            if (!company.isClosed()) return false;
        }
        return true;
    }

    public static boolean presidencyIsInPool(PublicCompany_1837 national) {
        // After a bankruptcy it is possible that the president certificate
        // is in the bank pool. In such a case we will not start.
        return national.getPresidentsShare().getOwner() != national.getRoot().getBank().getPool();
    }


    public void start(PublicCompany_1837 national, boolean atNewPhase, String nfrReportName) {
        this.national = national;
        this.atNewPhase = atNewPhase;
        this.nfrReportName = nfrReportName;

        PhaseManager phaseManager = getRoot().getPhaseManager();
        startNational = !national.hasStarted()
                && phaseManager.hasReachedPhase(national.getFormationStartPhase());
        forcedStart = !national.hasStarted()
                && phaseManager.hasReachedPhase(national.getForcedStartPhase());
        forcedMerge = !national.isComplete()
                && phaseManager.hasReachedPhase(national.getForcedMergePhase());

        minors = national.getMinors();
        startingMinor = national.getStartingMinor();
        currentPlayerOrder = new ArrayListState<>(this, "PlayerOrder_"+getId());
        minorsPerPlayer = ArrayListMultimapState.create(this, "MinorsPerPlayer_"+getId());
        //closedMinors = new ArrayListState<>(this, "ClosedMinorsPerMajor_"+getId());
        step = new GenericState<>(this, getId()+"_step",
                (national.hasStarted() ? Step.MERGE : Step.START));

        for (PublicCompany_1837 minor : minors) {
            if (!minor.isClosed()) minorsPerPlayer.put (minor.getPresident(), minor);
        }

        Player startingPlayer;
        if (national.hasStarted()) {
            startingPlayer = national.getPresident();
        } else {
            startingPlayer = startingMinor.getPresident();
        }

        currentPlayerOrder.clear();
        if (startingPlayer == null) {
            if (atNewPhase && (forcedStart || forcedMerge)) {
                // For all certainty; it has happened (1837 U1 in pool when starting Ug)
                startingPlayer = playerManager.getPriorityPlayer();
                currentPlayerOrder.add(startingPlayer);
            } else {
                finishRound();
                return;
            }
        } //else {
            for (Player player : playerManager.getNextPlayersAfter(
                    startingPlayer, true, false)) {
                for (PublicCompany_1837 minor : minors) {
                    if (!minor.isClosed() && player == minor.getPresident()
                            && !currentPlayerOrder.contains(player)) {
                        currentPlayerOrder.add(player);
                        // Once in the list is enough
                        break;
                    }
                }
            }
        //}

        ReportBuffer.add(this, LocalText.getText("StartFormationRound", national.getId(), nfrReportName));

        start();
    }

    @Override
    public void start() {
        startNational = !national.hasStarted();
        mergeNational = !nationalIsComplete(national);

        log.debug("StartNational={} forcedStart={} mergeNational={} forcedMerge={}", startNational, forcedStart, mergeNational, forcedMerge);

        if (step.value() == Step.START) {

            if (forcedStart) {
                executeStartNational(true);

                // The starting minor president becomes the initial major president
                if (startingMinor.getPresident() != null) {
                    setCurrentPlayer(startingMinor.getPresident());
                    national.setPresident(currentPlayer);
                    ReportBuffer.add(this, LocalText.getText("IS_NOW_PRES_OF",
                            currentPlayer.getId(),
                            national.getId()));
                } else if (national.isComplete()) {
                    finishRound();
                }
                startNational = false;
                step.set(Step.MERGE);
            }
       }

        if (step.value() == Step.MERGE) {
            if (forcedMerge) {  // TODO Below code to be replaced
                 for (PublicCompany_1837 minor : minors) {
                    if (!minor.isClosed()) {
                        mergeCompanies(minor, national,
                                minor == startingMinor,
                                forcedMerge);
                        // Replace the home token
                        Mergers.exchangeMinorToken (gameManager, minor, national);
                    }
                 }
                 if (national.checkPresidency()) {
                     Owner newPresident = national.getPresident();
                     if (newPresident instanceof Player) {
                         floatCompany(national);
                     }
                 }

                // Check if the National must discard any trains
                if (national.getNumberOfTrains() > national.getCurrentTrainLimit()) {
                    step.set(Step.DISCARD_TRAINS);
                } else {
                    finishRound();
                }
            } else {
                findNextMergingPlayer(false);
            }
        }
    }

    @Override
    public boolean setPossibleActions() {

        if (step.value() == Step.START) {
            if (startingMinor.isClosed()) {
                // Can occur after a bankruptcy
                startingPlayer = national.getPresident();
            } else {
                startingPlayer = startingMinor.getPresident();
            }
            setCurrentPlayer(startingPlayer); // Duplicate
            ReportBuffer.add(this, LocalText.getText("StartingPlayer",
                    startingPlayer.getId()
                    ));

            possibleActions.add(new FoldIntoNational(national, startingMinor));

        } else if (step.value() == Step.MERGE) {

            List<Company> minors = minorsPerPlayer.get (currentPlayer);
            possibleActions.add(new FoldIntoNational(national, minors));

        } else if (step.value() == Step.DISCARD_TRAINS) {

            if (national.getNumberOfTrains() > national.getCurrentTrainLimit()) {
                log.debug("+++ National {} has {}, limit is {}", national.getLongName(),
                        national.getNumberOfTrains(), national.getCurrentTrainLimit());
                possibleActions.add(new DiscardTrain(national,
                        national.getPortfolioModel().getUniqueTrains(), true));
            }
        }
        return true;
    }

    @Override
    protected boolean processGameSpecificAction(PossibleAction action) {

        if (action instanceof FoldIntoNational) {

            foldIntoNational((FoldIntoNational) action);

            return true;

        } else if (action instanceof DiscardTrain) {

            discardTrain ((DiscardTrain) action);
            return true;

        } else {
            return false;
        }
    }

    protected boolean findNextMergingPlayer(boolean skipCurrentPlayer) {

        if (currentPlayerOrder.isEmpty()) {
            return false;
        } else {
            // Find the next player to act with the current major
            Player nextPlayer = currentPlayerOrder.get(0);
            setCurrentPlayer(nextPlayer);
            return true;
        }

    }

    private boolean foldIntoNational(FoldIntoNational action) {

        // Validate
        String errMsg = null;

        List<PublicCompany_1837> folded = new ArrayList<>();
        if (action.getFoldedCompanies() != null) {
            for (Company comp : action.getFoldedCompanies()) {
                folded.add ((PublicCompany_1837) comp);
            }
        }
        boolean folding = !folded.isEmpty();
        boolean toStart = !national.hasStarted() && folded.contains(national.getStartingMinor());

        if (!folding) {
            ReportBuffer.add (this, LocalText.getText("NoMerge",
                    currentPlayer.getId(), action.getFoldableCompanyNames(), national));

            if (currentPlayer == startingPlayer) {
                // Does not want to start
                finishRound();
                return true;
            }
            currentPlayerOrder.remove(currentPlayer);

            // Does not want to merge
            if (!findNextMergingPlayer(true)) {
                if (national.hasStarted()) {
                    national.checkPresidency();
                }
                finishRound();
            }
            return true;
        }

        boolean starting = folded.contains(startingMinor);

CHECK:  while (true) {

            if (!starting && !national.hasStarted()) {
                errMsg = LocalText.getText("NotYetStarted",
                        national.getId());
                break;
            }

            for (Company comp : folded) {
                if (!minors.contains(comp)) {
                    errMsg = LocalText.getText("WrongCompany", comp.getId(),
                            action.getFoldableCompanyNames());
                    break CHECK;
                }
            }

            if (starting && !toStart) {
                errMsg = LocalText.getText("CompanyAlreadyStarted", national);
                break;
            } else if (!starting && toStart) {
                errMsg = LocalText.getText("WrongCompany",
                        action.getFoldedCompanyNames(),
                        startingMinor.getId());
                break;
            }
            break;
        }

        if (errMsg != null) {
            DisplayBuffer.add(this, LocalText.getText("CannotMerge",
                    action.getFoldedCompanyNames(),
                    national.getId(),
                    errMsg));
            return false;
        }

        // all actions linked during formation round to avoid serious undo problems

        // FIXME: changeStack.linkToPreviousMoveSet();

        if (folding) {
            if (starting) {
                executeStartNational(false);
                step.set(Step.MERGE);
            }
            executeMergeMinors(folded);
        }

        // Remove a current player who has no more minors to check
        if (minorsPerPlayer.get(currentPlayer).isEmpty()) {
            currentPlayerOrder.remove (currentPlayer);
            if (!findNextMergingPlayer(true)) {
                if (national.hasStarted()) {
                    national.checkPresidency();
                }
                finishRound();
            }
        }

        return folding;
    }

    private void executeStartNational(boolean display) {

        national.start();
        String message = LocalText.getText("START_MERGED_COMPANY",
                national.getId(),
                Bank.format(this, national.getIPOPrice()),
                national.getStartSpace());
        ReportBuffer.add(this, message);
        if (display) DisplayBuffer.add(this, message);

        floatCompany(national);
    }

    private void executeMergeMinors (List<PublicCompany_1837> minorsToMerge) {

        for (PublicCompany_1837 minor : minorsToMerge) {

            mergeCompanies(minor, national,
                    minor == startingMinor, false);

            // Replace the home token
            Mergers.exchangeMinorToken (gameManager, minor, national);

            //closedMinors.add (minor);
            // Remove minor
            minorsPerPlayer.remove(currentPlayer, minor);
        }

        if (atNewPhase) national.setOperated();
    }

   public boolean discardTrain(DiscardTrain action) {

        if (!action.process(this)) return false;

        // We still might have another excess train
        // TODO: would be better to have DiscardTrain discard multiple trains
        if (national.getNumberOfTrains() > national.getCurrentTrainLimit()) {
            step.set (Step.DISCARD_TRAINS);
        } else {
            finishRound();
        }

        return true;
    }

    @Override
    protected void finishRound() {
        RoundFacade interruptedRound = gameManager.getInterruptedRound();
        ReportBuffer.add(this, " ");
        if (interruptedRound != null) {
            ReportBuffer.add(this, LocalText.getText("EndOfFormationRound",
                    national.getId(), interruptedRound.getRoundName()));
        } else {
            ReportBuffer.add(this, LocalText.getText("EndOfFormationRoundNoInterrupt",
                    national.getId(), nfrReportName));
        }

        if (national.hasStarted()) national.checkPresidency();
        //national.setOperated(); // was a duplicate

        // Inform GameManager
        gameManager.nextRound(this);
    }

    public PublicCompany_1837 getNational() {
        return national;
    }

    @Override
    public String toString() {
        return getId();
    }

}
