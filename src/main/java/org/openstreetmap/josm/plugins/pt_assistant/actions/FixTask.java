// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.pt_assistant.actions;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.SwingUtilities;

import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
/**
 * This class was copied with minor changes from ValidatorDialog.FixTask
 *
 * FIXME: remove this copy/paste by extending JOSM core class
 *
 * @author darya
 *
 */
public class FixTask extends PleaseWaitRunnable {

    private final Collection<TestError> testErrors;
    private final List<Command> fixCommands = new ArrayList<>();
    private boolean canceled;

    public FixTask(Collection<TestError> testErrors) {
        super(tr("Fixing errors …"), false);
        this.testErrors = testErrors == null ? new ArrayList<>() : testErrors;
    }

    @Override
    protected void cancel() {
        this.canceled = true;
    }

    @Override
    protected void finish() {
        // do nothing
    }

    protected void fixError(TestError error) throws InterruptedException, InvocationTargetException {
        if (error.isFixable()) {
            final Command fixCommand = error.getFix();
            if (fixCommand != null) {
                SwingUtilities.invokeAndWait(() -> {
                    UndoRedoHandler.getInstance().addNoRedraw(fixCommand);
                    fixCommands.add(fixCommand);
                });
            }
            // It is wanted to ignore an error if it said fixable, even if
            // fixCommand was null
            // This is to fix #5764 and #5773:
            // a delete command, for example, may be null if all concerned
            // primitives have already been deleted
            error.setIgnored(true);
        }
    }

    @Override
    protected void realRun() {
        ProgressMonitor monitor = getProgressMonitor();
        try {
            monitor.setTicksCount(testErrors.size());
            int i = 0;
            SwingUtilities.invokeAndWait(() -> MainApplication.getLayerManager().getEditDataSet().beginUpdate());
            try {
                for (TestError error : testErrors) {
                    i++;
                    monitor.subTask(tr("Fixing ({0}/{1}): ''{2}''", i, testErrors.size(), error.getMessage()));
                    if (this.canceled)
                        return;
                    fixError(error);
                    monitor.worked(1);
                }
            } finally {
                SwingUtilities.invokeAndWait(() -> MainApplication.getLayerManager().getEditDataSet().endUpdate());
            }
            monitor.subTask(tr("Updating map ..."));
            SwingUtilities.invokeAndWait(() -> {
                UndoRedoHandler.getInstance().afterAdd(fixCommands);
                MainApplication.getMap().repaint();
            });
        } catch (InterruptedException | InvocationTargetException e) {
            // FIXME: signature of realRun should have a generic checked
            // exception we could throw here
            throw new RuntimeException(e);
        } finally {
            monitor.finishTask();
        }

    }

}
