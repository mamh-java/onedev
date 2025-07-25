package io.onedev.server.web.component.build.list;

import static io.onedev.server.model.Build.SORT_FIELDS;
import static io.onedev.server.web.translation.Translation._T;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.apache.wicket.Component;
import org.apache.wicket.Session;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.event.Broadcast;
import org.apache.wicket.event.IEvent;
import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.DataTable;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.extensions.markup.html.repeater.util.SortableDataProvider;
import org.apache.wicket.feedback.FencedFeedbackPanel;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.OddEvenItem;
import org.apache.wicket.markup.repeater.RepeatingView;
import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.eclipse.jgit.lib.FileMode;

import com.google.common.collect.Sets;

import io.onedev.commons.utils.ExplicitException;
import io.onedev.server.OneDev;
import io.onedev.server.data.migration.VersionedXmlDoc;
import io.onedev.server.entitymanager.AuditManager;
import io.onedev.server.entitymanager.BuildManager;
import io.onedev.server.entitymanager.BuildParamManager;
import io.onedev.server.entitymanager.ProjectManager;
import io.onedev.server.entitymanager.SettingManager;
import io.onedev.server.git.BlobIdent;
import io.onedev.server.git.GitUtils;
import io.onedev.server.job.JobManager;
import io.onedev.server.model.Build;
import io.onedev.server.model.Build.Status;
import io.onedev.server.model.Project;
import io.onedev.server.model.support.administration.GlobalBuildSetting;
import io.onedev.server.persistence.TransactionManager;
import io.onedev.server.search.entity.EntityQuery;
import io.onedev.server.search.entity.EntitySort;
import io.onedev.server.search.entity.EntitySort.Direction;
import io.onedev.server.search.entity.build.BuildQuery;
import io.onedev.server.search.entity.build.FuzzyCriteria;
import io.onedev.server.security.SecurityUtils;
import io.onedev.server.security.permission.JobPermission;
import io.onedev.server.security.permission.RunJob;
import io.onedev.server.util.DateUtils;
import io.onedev.server.util.Input;
import io.onedev.server.web.WebConstants;
import io.onedev.server.web.WebSession;
import io.onedev.server.web.behavior.BuildQueryBehavior;
import io.onedev.server.web.behavior.ChangeObserver;
import io.onedev.server.web.component.build.ParamValuesLabel;
import io.onedev.server.web.component.build.status.BuildStatusIcon;
import io.onedev.server.web.component.datatable.DefaultDataTable;
import io.onedev.server.web.component.datatable.selectioncolumn.SelectionColumn;
import io.onedev.server.web.component.entity.labels.EntityLabelsPanel;
import io.onedev.server.web.component.floating.AlignPlacement;
import io.onedev.server.web.component.floating.Alignment;
import io.onedev.server.web.component.floating.ComponentTarget;
import io.onedev.server.web.component.floating.FloatingPanel;
import io.onedev.server.web.component.job.runselector.JobRunSelector;
import io.onedev.server.web.component.link.ActionablePageLink;
import io.onedev.server.web.component.link.DropdownLink;
import io.onedev.server.web.component.menu.MenuItem;
import io.onedev.server.web.component.menu.MenuLink;
import io.onedev.server.web.component.modal.ModalLink;
import io.onedev.server.web.component.modal.ModalPanel;
import io.onedev.server.web.component.modal.confirm.ConfirmModalPanel;
import io.onedev.server.web.component.project.selector.ProjectSelector;
import io.onedev.server.web.component.revision.RevisionSelector;
import io.onedev.server.web.component.savedquery.SavedQueriesClosed;
import io.onedev.server.web.component.savedquery.SavedQueriesOpened;
import io.onedev.server.web.component.sortedit.SortEditPanel;
import io.onedev.server.web.component.stringchoice.StringMultiChoice;
import io.onedev.server.web.page.builds.BuildListPage;
import io.onedev.server.web.page.project.blob.ProjectBlobPage;
import io.onedev.server.web.page.project.builds.ProjectBuildsPage;
import io.onedev.server.web.page.project.builds.detail.dashboard.BuildDashboardPage;
import io.onedev.server.web.page.project.pullrequests.detail.activities.PullRequestActivitiesPage;
import io.onedev.server.web.util.Cursor;
import io.onedev.server.web.util.LoadableDetachableDataProvider;
import io.onedev.server.web.util.QuerySaveSupport;
import io.onedev.server.web.util.paginghistory.PagingHistorySupport;

public abstract class BuildListPanel extends Panel {
	
	public static final String REF_TOD = "refs/onedev/tod";

	private final IModel<String> queryStringModel;
	
	private final boolean showRef;
	
	private final boolean showDuration;
	
	private final IModel<BuildQuery> queryModel = new LoadableDetachableModel<BuildQuery>() {

		@Override
		protected BuildQuery load() {
			return parse(queryStringModel.getObject(), getBaseQuery());
		}
		
	};
	
	private Component countLabel;
	
	private DataTable<Build, Void> buildsTable;
	
	private SelectionColumn<Build, Void> selectionColumn;
	
	private SortableDataProvider<Build, Void> dataProvider;	
	
	private TextField<String> queryInput;
	
	private Component runJobLink;
	
	private WebMarkupContainer body;
	
	private Component saveQueryLink;
	
	private boolean querySubmitted = true;
	
	public BuildListPanel(String id, IModel<String> queryModel, boolean showRef,
						  boolean showDuration) {
		super(id);
		this.queryStringModel = queryModel;
		this.showRef = showRef;
		this.showDuration = showDuration;
	}
	
	private BuildManager getBuildManager() {
		return OneDev.getInstance(BuildManager.class);
	}

	private ProjectManager getProjectManager() {
		return OneDev.getInstance(ProjectManager.class);
	}

	private TransactionManager getTransactionManager() {
		return OneDev.getInstance(TransactionManager.class);
	}

	private AuditManager getAuditManager() {
		return OneDev.getInstance(AuditManager.class);
	}
	
	@Nullable
	private BuildQuery parse(@Nullable String queryString, BuildQuery baseQuery) {
		BuildQuery parsedQuery;
		try {
			parsedQuery = BuildQuery.parse(getProject(), queryString, true, true);
		} catch (Exception e) {
			getFeedbackMessages().clear();
			if (e instanceof ExplicitException) {
				error(e.getMessage());
				return null;
			} else {
				info("Performing fuzzy query. Enclosing search text with '~' to add more conditions, for instance: ~text to search~ and successful");
				parsedQuery = new BuildQuery(new FuzzyCriteria(queryString));
			}
		}
		return BuildQuery.merge(baseQuery, parsedQuery);
	}
	
	@Override
	protected void onDetach() {
		queryStringModel.detach();
		queryModel.detach();
		super.onDetach();
	}
	
	@Nullable
	protected abstract Project getProject();

	protected BuildQuery getBaseQuery() {
		return new BuildQuery();
	}

	@Nullable
	protected PagingHistorySupport getPagingHistorySupport() {
		return null;
	}
	
	@Nullable
	protected QuerySaveSupport getQuerySaveSupport() {
		return null;
	}

	private GlobalBuildSetting getGlobalBuildSetting() {
		return OneDev.getInstance(SettingManager.class).getBuildSetting();
	}
	
	private void doQuery(AjaxRequestTarget target) {
		buildsTable.setCurrentPage(0);
		target.add(countLabel);
		target.add(body);
		if (selectionColumn != null)
			selectionColumn.getSelections().clear();
		querySubmitted = true;
		if (SecurityUtils.getAuthUser() != null && getQuerySaveSupport() != null)
			target.add(saveQueryLink);
	}
	
	@Override
	protected void onInitialize() {
		super.onInitialize();

		add(new AjaxLink<Void>("showSavedQueries") {

			@Override
			public void onEvent(IEvent<?> event) {
				super.onEvent(event);
				if (event.getPayload() instanceof SavedQueriesClosed) {
					((SavedQueriesClosed) event.getPayload()).getHandler().add(this);
				}
			}
			
			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisible(getQuerySaveSupport() != null && !getQuerySaveSupport().isSavedQueriesVisible());
			}

			@Override
			public void onClick(AjaxRequestTarget target) {
				send(getPage(), Broadcast.BREADTH, new SavedQueriesOpened(target));
				target.add(this);
			}
			
		}.setOutputMarkupPlaceholderTag(true));
		
		add(saveQueryLink = new AjaxLink<Void>("saveQuery") {

			@Override
			protected void onConfigure() {
				super.onConfigure();
				setEnabled(querySubmitted && queryModel.getObject() != null);
				setVisible(SecurityUtils.getAuthUser() != null && getQuerySaveSupport() != null);
			}

			@Override
			protected void onComponentTag(ComponentTag tag) {
				super.onComponentTag(tag);
				configure();
				if (!isEnabled()) 
					tag.append("class", "disabled", " ");
				if (!querySubmitted)
					tag.put("data-tippy-content", _T("Query not submitted"));
				else if (queryModel.getObject() == null)
					tag.put("data-tippy-content", _T("Can not save malformed query"));
			}

			@Override
			public void onClick(AjaxRequestTarget target) {
				getQuerySaveSupport().onSaveQuery(target, queryModel.getObject().toString());
			}		
			
		}.setOutputMarkupPlaceholderTag(true));
		
		add(new MenuLink("operations") {

			@Override
			protected List<MenuItem> getMenuItems(FloatingPanel dropdown) {
				List<MenuItem> menuItems = new ArrayList<>();
				
				menuItems.add(new MenuItem() {

					@Override
					public String getLabel() {
						return _T("Cancel Selected Builds");
					}
					
					@Override
					public WebMarkupContainer newLink(String id) {
						return new AjaxLink<Void>(id) {

							@Override
							public void onClick(AjaxRequestTarget target) {
								dropdown.close();
								
								String errorMessage = null;
								for (IModel<Build> each: selectionColumn.getSelections()) {
									Build build = each.getObject();
									if (build.isFinished()) {
										errorMessage = MessageFormat.format(_T("Build #{0} already finished"), build.getNumber());
										break;
									} 
								}
								
								if (errorMessage != null) {
									getSession().error(errorMessage);
								} else {
									new ConfirmModalPanel(target) {
										
										@Override
										protected void onConfirm(AjaxRequestTarget target) {
											for (IModel<Build> each: selectionColumn.getSelections()) 
												OneDev.getInstance(JobManager.class).cancel(each.getObject());
											Session.get().success(_T("Cancel request submitted"));
										}
										
										@Override
										protected String getConfirmMessage() {
											return _T("Type <code>yes</code> below to cancel selected builds");
										}
										
										@Override
										protected String getConfirmInput() {
											return "yes"; 
										}
										
									};
								}
								
							}
							
							@Override
							protected void onConfigure() {
								super.onConfigure();
								setEnabled(!selectionColumn.getSelections().isEmpty());
							}
							
							@Override
							protected void onComponentTag(ComponentTag tag) {
								super.onComponentTag(tag);
								configure();
								if (!isEnabled()) {
									tag.put("disabled", "disabled");
									tag.put("data-tippy-content", _T("Please select builds to cancel"));
								}
							}
							
						};
					}
					
				});
				
				menuItems.add(new MenuItem() {

					@Override
					public String getLabel() {
						return _T("Re-run Selected Builds");
					}
					
					@Override
					public WebMarkupContainer newLink(String id) {
						return new AjaxLink<Void>(id) {

							@Override
							public void onClick(AjaxRequestTarget target) {
								dropdown.close();
								
								String errorMessage = null;
								for (IModel<Build> each: selectionColumn.getSelections()) {
									Build build = each.getObject();
									if (!build.isFinished()) {
										errorMessage = MessageFormat.format(_T("Build #{0} not finished yet"), build.getNumber());
										break;
									} 
								}
								
								if (errorMessage != null) {
									getSession().error(errorMessage);
								} else {
									new ConfirmModalPanel(target) {
										
										@Override
										protected void onConfirm(AjaxRequestTarget target) {
											for (IModel<Build> each: selectionColumn.getSelections()) { 
												Build build = each.getObject();
												OneDev.getInstance(JobManager.class).resubmit(build, "Resubmitted manually");
											}
											Session.get().success(_T("Re-run request submitted"));
										}
										
										@Override
										protected String getConfirmMessage() {
											return _T("Type <code>yes</code> below to re-run selected builds");
										}
										
										@Override
										protected String getConfirmInput() {
											return "yes"; 
										}
										
									};
								}
								
							}
							
							@Override
							protected void onConfigure() {
								super.onConfigure();
								setEnabled(!selectionColumn.getSelections().isEmpty());
							}
							
							@Override
							protected void onComponentTag(ComponentTag tag) {
								super.onComponentTag(tag);
								configure();
								if (!isEnabled()) {
									tag.put("disabled", "disabled");
									tag.put("data-tippy-content", _T("Please select builds to re-run"));
								}
							}
							
						};
					}
					
				});
				
				menuItems.add(new MenuItem() {

					@Override
					public String getLabel() {
						return _T("Delete Selected Builds");
					}
					
					@Override
					public WebMarkupContainer newLink(String id) {
						return new AjaxLink<Void>(id) {

							@Override
							public void onClick(AjaxRequestTarget target) {
								dropdown.close();
								
								new ConfirmModalPanel(target) {
									
									@Override
									protected void onConfirm(AjaxRequestTarget target) {
										getTransactionManager().run(()-> {
											Collection<Build> builds = new ArrayList<>();
											for (IModel<Build> each: selectionColumn.getSelections())
												builds.add(each.getObject());
											getBuildManager().delete(builds);
											for (var build: builds) {
												var oldAuditContent = VersionedXmlDoc.fromBean(build).toXML();
												getAuditManager().audit(build.getProject(), "deleted build \"" + build.getReference().toString(build.getProject()) + "\"", oldAuditContent, null);
											}												
										});

										target.add(countLabel);
										target.add(body);
										selectionColumn.getSelections().clear();
									}
									
									@Override
									protected String getConfirmMessage() {
										return _T("Type <code>yes</code> below to delete selected builds");
									}
									
									@Override
									protected String getConfirmInput() {
										return "yes";
									}
									
								};
								
							}
							
							@Override
							protected void onConfigure() {
								super.onConfigure();
								setEnabled(!selectionColumn.getSelections().isEmpty());
							}
							
							@Override
							protected void onComponentTag(ComponentTag tag) {
								super.onComponentTag(tag);
								configure();
								if (!isEnabled()) {
									tag.put("disabled", "disabled");
									tag.put("data-tippy-content", _T("Please select builds to delete"));
								}
							}
							
						};
					}
					
				});
				
				menuItems.add(new MenuItem() {
					
					@Override
					public String getLabel() {
						return _T("Cancel All Queried Builds");
					}
					
					@Override
					public WebMarkupContainer newLink(String id) {
						return new AjaxLink<Void>(id) {

							@SuppressWarnings("unchecked")
							@Override
							public void onClick(AjaxRequestTarget target) {
								dropdown.close();
								
								String errorMessage = null;
								for (Iterator<Build> it = (Iterator<Build>) dataProvider.iterator(0, buildsTable.getItemCount()); it.hasNext();) { 
									Build build = it.next();
									if (build.isFinished()) {
										errorMessage = MessageFormat.format(_T("Build #{0} already finished"), build.getNumber());
										break;
									}
								}
								
								if (errorMessage != null) {
									getSession().error(errorMessage);
								} else {
									new ConfirmModalPanel(target) {
										
										@Override
										protected void onConfirm(AjaxRequestTarget target) {
											for (Iterator<Build> it = (Iterator<Build>) dataProvider.iterator(0, buildsTable.getItemCount()); it.hasNext();) 
												OneDev.getInstance(JobManager.class).cancel(it.next());
											Session.get().success(_T("Cancel request submitted"));
										}
										
										@Override
										protected String getConfirmMessage() {
											return _T("Type <code>yes</code> below to cancel all queried builds");
										}
										
										@Override
										protected String getConfirmInput() {
											return "yes";
										}
										
									};
								}
								
							}
							
							@Override
							protected void onConfigure() {
								super.onConfigure();
								setEnabled(buildsTable.getItemCount() != 0);
							}
							
							@Override
							protected void onComponentTag(ComponentTag tag) {
								super.onComponentTag(tag);
								configure();
								if (!isEnabled()) {
									tag.put("disabled", "disabled");
									tag.put("data-tippy-content", _T("No builds to cancel"));
								}
							}
							
						};
					}
					
				});

				menuItems.add(new MenuItem() {
					
					@Override
					public String getLabel() {
						return _T("Re-run All Queried Builds");
					}
					
					@Override
					public WebMarkupContainer newLink(String id) {
						return new AjaxLink<Void>(id) {

							@SuppressWarnings("unchecked")
							@Override
							public void onClick(AjaxRequestTarget target) {
								dropdown.close();
								
								String errorMessage = null;
								for (Iterator<Build> it = (Iterator<Build>) dataProvider.iterator(0, buildsTable.getItemCount()); it.hasNext();) { 
									Build build = it.next();
									if (!build.isFinished()) {
										errorMessage = MessageFormat.format(_T("Build #{0} not finished yet"), build.getNumber());
										break;
									}
								}
								
								if (errorMessage != null) {
									getSession().error(errorMessage);
								} else {
									new ConfirmModalPanel(target) {
										
										@Override
										protected void onConfirm(AjaxRequestTarget target) {
											for (Iterator<Build> it = (Iterator<Build>) dataProvider.iterator(0, buildsTable.getItemCount()); it.hasNext();) 
												OneDev.getInstance(JobManager.class).resubmit(it.next(), "Resubmitted manually");
											Session.get().success(_T("Re-run request submitted"));
										}
										
										@Override
										protected String getConfirmMessage() {
											return _T("Type <code>yes</code> below to re-run all queried builds");
										}
										
										@Override
										protected String getConfirmInput() {
											return "yes";
										}
										
									};
								}
								
							}
							
							@Override
							protected void onConfigure() {
								super.onConfigure();
								setEnabled(buildsTable.getItemCount() != 0);
							}
							
							@Override
							protected void onComponentTag(ComponentTag tag) {
								super.onComponentTag(tag);
								configure();
								if (!isEnabled()) {
									tag.put("disabled", "disabled");
									tag.put("data-tippy-content", _T("No builds to re-run"));
								}
							}
							
						};
					}
					
				});
				
				menuItems.add(new MenuItem() {

					@Override
					public String getLabel() {
						return _T("Delete All Queried Builds");
					}
					
					@Override
					public WebMarkupContainer newLink(String id) {
						return new AjaxLink<Void>(id) {

							@SuppressWarnings("unchecked")
							@Override
							public void onClick(AjaxRequestTarget target) {
								dropdown.close();
								
								new ConfirmModalPanel(target) {
									
									@Override
									protected void onConfirm(AjaxRequestTarget target) {
										getTransactionManager().run(()-> {
											Collection<Build> builds = new ArrayList<>();
											for (Iterator<Build> it = (Iterator<Build>) dataProvider.iterator(0, buildsTable.getItemCount()); it.hasNext();) {
												builds.add(it.next());
											}
											getBuildManager().delete(builds);
											for (var build: builds) {
												var oldAuditContent = VersionedXmlDoc.fromBean(build).toXML();
												getAuditManager().audit(build.getProject(), "deleted build \"" + build.getReference().toString(build.getProject()) + "\"", oldAuditContent, null);
											}												
										});
										dataProvider.detach();
										target.add(countLabel);
										target.add(body);
										selectionColumn.getSelections().clear();
									}
									
									@Override
									protected String getConfirmMessage() {
										return _T("Type <code>yes</code> below to delete all queried builds");
									}
									
									@Override
									protected String getConfirmInput() {
										return "yes";
									}
									
								};
								
							}
							
							@Override
							protected void onConfigure() {
								super.onConfigure();
								setEnabled(buildsTable.getItemCount() != 0);
							}
							
							@Override
							protected void onComponentTag(ComponentTag tag) {
								super.onComponentTag(tag);
								configure();
								if (!isEnabled()) {
									tag.put("disabled", "disabled");
									tag.put("data-tippy-content", _T("No builds to delete"));
								}
							}
							
						};
					}
					
				});
				
				return menuItems;
			}

			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisible(getProject() != null && SecurityUtils.canManageBuilds(getProject()));
			}
			
		});
		
		add(new ModalLink("listParams") {

			private List<String> listParams;
			
			@Override
			protected Component newContent(String id, ModalPanel modal) {
				Fragment fragment = new Fragment(id, "listParamsFrag", BuildListPanel.this);
				Form<?> form = new Form<Void>("form");
				listParams = getListParams();
				form.add(new StringMultiChoice("params", new IModel<Collection<String>>() {

					@Override
					public void detach() {
					}

					@Override
					public Collection<String> getObject() {
						return listParams;
					}

					@Override
					public void setObject(Collection<String> object) {
						listParams = new ArrayList<>(object);
					}
					
				}, new LoadableDetachableModel<List<String>>() {

					@Override
					protected List<String> load() {
						var paramNames = new ArrayList<>(OneDev.getInstance(BuildParamManager.class).getParamNames(getProject()));
						Collections.sort(paramNames);
						return paramNames;
					}
					
				}, false));
				
				form.add(new AjaxLink<Void>("close") {

					@Override
					public void onClick(AjaxRequestTarget target) {
						modal.close();
					}
					
				});
				
				form.add(new AjaxButton("save") {

					@Override
					protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
						super.onSubmit(target, form);
						if (getProject() != null) {
							var oldAuditContent = VersionedXmlDoc.fromBean(getProject().getBuildSetting().getListParams(true)).toXML();
							getProject().getBuildSetting().setListParams(listParams);
							var newAuditContent = VersionedXmlDoc.fromBean(getProject().getBuildSetting().getListParams(true)).toXML();
							getProjectManager().update(getProject());
							getAuditManager().audit(getProject(), "changed display params of build list", oldAuditContent, newAuditContent);
						} else {
							var oldAuditContent = VersionedXmlDoc.fromBean(getGlobalBuildSetting().getListParams()).toXML();
							getGlobalBuildSetting().setListParams(listParams);
							var newAuditContent = VersionedXmlDoc.fromBean(getGlobalBuildSetting().getListParams()).toXML();
							OneDev.getInstance(SettingManager.class).saveBuildSetting(getGlobalBuildSetting());
							getAuditManager().audit(null, "changed display params of build list", oldAuditContent, newAuditContent);
						}
						setResponsePage(getPage().getClass(), getPage().getPageParameters());
					}
					
				});
				
				form.add(new AjaxLink<Void>("useDefault") {

					@Override
					public void onClick(AjaxRequestTarget target) {
						modal.close();
						var oldAuditContent = VersionedXmlDoc.fromBean(getProject().getBuildSetting().getListParams(true)).toXML();
						getProject().getBuildSetting().setListParams(null);
						var newAuditContent = VersionedXmlDoc.fromBean(getProject().getBuildSetting().getListParams(true)).toXML();
						getProjectManager().update(getProject());
						getAuditManager().audit(getProject(), "changed display params of build list", oldAuditContent, newAuditContent);
						target.add(body);
					}
					
				}.setVisible(getProject() != null && getProject().getBuildSetting().getListParams(false) != null));
				
				form.add(new AjaxLink<Void>("cancel") {

					@Override
					public void onClick(AjaxRequestTarget target) {
						modal.close();
					}
					
				});

				fragment.add(form);
				
				return fragment;
			}

			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisible(SecurityUtils.isAdministrator() 
						|| getProject() != null && SecurityUtils.canManageBuilds(getProject()));
			}
			
		});		
		
		add(new DropdownLink("filter") {
			@Override
			protected Component newContent(String id, FloatingPanel dropdown) {
				return new BuildFilterPanel(id, new IModel<EntityQuery<Build>>() {
					@Override
					public void detach() {
					}
					@Override
					public EntityQuery<Build> getObject() {
						var query = parse(queryStringModel.getObject(), new BuildQuery());
						return query!=null? query : new BuildQuery();
					}
					@Override
					public void setObject(EntityQuery<Build> object) {
						BuildListPanel.this.getFeedbackMessages().clear();
						queryStringModel.setObject(object.toString());
						var target = RequestCycle.get().find(AjaxRequestTarget.class);
						target.add(queryInput);
						doQuery(target);
					}
				}) {

					@Override
					protected Project getProject() {
						return BuildListPanel.this.getProject();
					}
					
				};
			}
		});

		add(new DropdownLink("orderBy") {

			@Override
			protected Component newContent(String id, FloatingPanel dropdown) {
				Map<String, Direction> sortFields = new LinkedHashMap<>();
				for (var entry: SORT_FIELDS.entrySet())
					sortFields.put(entry.getKey(), entry.getValue().getDefaultDirection());
				if (getProject() != null)
					sortFields.remove(Build.NAME_PROJECT);
				
				return new SortEditPanel<Build>(id, sortFields, new IModel<>() {

					@Override
					public void detach() {
					}

					@Override
					public List<EntitySort> getObject() {
						BuildQuery query = parse(queryStringModel.getObject(), new BuildQuery());
						return query!=null? query.getSorts() : new ArrayList<>();
					}

					@Override
					public void setObject(List<EntitySort> object) {
						BuildQuery query = parse(queryStringModel.getObject(), new BuildQuery());
						BuildListPanel.this.getFeedbackMessages().clear();
						if (query == null)
							query = new BuildQuery();
						query.setSorts(object);
						queryStringModel.setObject(query.toString());
						AjaxRequestTarget target = RequestCycle.get().find(AjaxRequestTarget.class);
						target.add(queryInput);
						doQuery(target);
					}

				});
			}
			
		});	
		
		var extraActionsView = new RepeatingView("extraActions");
		add(extraActionsView);
		for (var renderer: OneDev.getExtensions(BuildListActionRenderer.class)) 
			extraActionsView.add(renderer.render(extraActionsView.newChildId()));
		
		queryInput = new TextField<String>("input", queryStringModel);
		queryInput.add(new BuildQueryBehavior(new AbstractReadOnlyModel<Project>() {

			@Override
			public Project getObject() {
				return getProject();
			}
			
		}, true, true, true) {
			
			@Override
			protected void onInput(AjaxRequestTarget target, String inputContent) {
				BuildListPanel.this.getFeedbackMessages().clear();
				querySubmitted = StringUtils.trimToEmpty(queryStringModel.getObject())
						.equals(StringUtils.trimToEmpty(inputContent));
				target.add(saveQueryLink);
			}
			
		});
		
		queryInput.add(new AjaxFormComponentUpdatingBehavior("clear") {
			
			@Override
			protected void onUpdate(AjaxRequestTarget target) {
				doQuery(target);
			}
			
		});
		
		Form<?> queryForm = new Form<Void>("query");
		queryForm.add(queryInput);
		queryForm.add(new AjaxButton("submit") {

			@Override
			protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
				super.onSubmit(target, form);
				BuildListPanel.this.getFeedbackMessages().clear();
				doQuery(target);
			}
			
		});
		add(queryForm);

		if (getProject() == null) {
			add(runJobLink = new DropdownLink("runJob") {

				@Override
				protected Component newContent(String id, FloatingPanel dropdown) {
					return new ProjectSelector(id, new LoadableDetachableModel<>() {

						@Override
						protected List<Project> load() {
							List<Project> projects = new ArrayList<>(SecurityUtils.getAuthorizedProjects(new JobPermission(null, new RunJob())));
							projects.sort(getProjectManager().cloneCache().comparingPath());
							return projects;
						}

					}) {
						@Override
						protected String getTitle() {
							return "Select Project";
						}

						@Override
						protected void onSelect(AjaxRequestTarget target, Project project) {
							dropdown.close();
							newRevisionSelector(target, project);
						}

					}.add(AttributeAppender.append("class", "no-current"));
				}

				@Override
				protected void onConfigure() {
					super.onConfigure();
					setVisible(SecurityUtils.getAuthUser() != null && getPage() instanceof BuildListPage);
				}
				
			});
		} else {
			add(runJobLink = new AjaxLink<Void>("runJob") {

				@Override
				public void onClick(AjaxRequestTarget target) {
					newRevisionSelector(target, getProject());
				}

				@Override
				protected void onConfigure() {
					super.onConfigure();
					setVisible(SecurityUtils.getAuthUser() != null && getPage() instanceof ProjectBuildsPage);
				}
				
			});
		}

		add(countLabel = new Label("count", new AbstractReadOnlyModel<String>() {
			@Override
			public String getObject() {
				if (dataProvider.size() > 1)
					return MessageFormat.format(_T("found {0} builds"), dataProvider.size());
				else
					return _T("found 1 build");					
			}
		}) {
			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisible(dataProvider.size() != 0);
			}
		}.setOutputMarkupPlaceholderTag(true));
		
		dataProvider = new LoadableDetachableDataProvider<>() {

			@Override
			public Iterator<? extends Build> iterator(long first, long count) {
				try {
					return getBuildManager().query(getProject(), queryModel.getObject(),
							true, (int) first, (int) count).iterator();
				} catch (ExplicitException e) {
					error(e.getMessage());
					return new ArrayList<Build>().iterator();
				}
			}

			@Override
			public long calcSize() {
				BuildQuery query = queryModel.getObject();
				if (query != null) {
					try {
						return getBuildManager().count(getProject(), query.getCriteria());
					} catch (ExplicitException e) {
						error(e.getMessage());
					}
				}
				return 0;
			}

			@Override
			public IModel<Build> model(Build object) {
				Long buildId = object.getId();
				return new LoadableDetachableModel<Build>() {

					@Override
					protected Build load() {
						return getBuildManager().load(buildId);
					}

				};
			}

		};
				
		body = new WebMarkupContainer("body");
		add(body.setOutputMarkupId(true));
		
		body.add(new FencedFeedbackPanel("feedback", this));
		
		List<IColumn<Build, Void>> columns = new ArrayList<>();
		
		if (getProject() != null && SecurityUtils.canManageBuilds(getProject())) 
			columns.add(selectionColumn = new SelectionColumn<Build, Void>());
		
		columns.add(new AbstractColumn<>(Model.of(_T("Build"))) {

			@Override
			public String getCssClass() {
				return "build";
			}

			@Override
			public void populateItem(Item<ICellPopulator<Build>> cellItem, String componentId, IModel<Build> rowModel) {
				Fragment fragment = new Fragment(componentId, "buildFrag", BuildListPanel.this);
				Build build = rowModel.getObject();
				Long buildId = build.getId();

				WebMarkupContainer link = new ActionablePageLink("link",
						BuildDashboardPage.class, BuildDashboardPage.paramsOf(build)) {

					@Override
					protected void doBeforeNav(AjaxRequestTarget target) {
						OddEvenItem<?> row = cellItem.findParent(OddEvenItem.class);
						Cursor cursor = new Cursor(queryModel.getObject().toString(), (int) buildsTable.getItemCount(),
								(int) buildsTable.getCurrentPage() * WebConstants.PAGE_SIZE + row.getIndex(), getProject());
						WebSession.get().setBuildCursor(cursor);

						String directUrlAfterDelete = RequestCycle.get().urlFor(
								getPage().getClass(), getPage().getPageParameters()).toString();
						WebSession.get().setRedirectUrlAfterDelete(Build.class, directUrlAfterDelete);
					}

				};
				link.add(new BuildStatusIcon("icon", new LoadableDetachableModel<Status>() {

					@Override
					protected Status load() {
						return getBuildManager().load(buildId).getStatus();
					}

				}));
				link.add(new Label("summary", new AbstractReadOnlyModel<String>() {

					@Override
					public String getObject() {
						return rowModel.getObject().getSummary(getProject());
					}

				}));
				fragment.add(link);

				fragment.add(new EntityLabelsPanel<>("labels", rowModel));

				fragment.add(newBuildObserver(buildId));
				fragment.setOutputMarkupId(true);
				cellItem.add(fragment);
			}
		});
		
		if (showRef) {
			columns.add(new AbstractColumn<>(Model.of(_T("On Behalf Of"))) {

				@Override
				public String getCssClass() {
					return "on-behalf-of d-none d-lg-table-cell";
				}

				@Override
				public void populateItem(Item<ICellPopulator<Build>> cellItem, String componentId,
										 IModel<Build> rowModel) {
					Build build = rowModel.getObject();
					if (SecurityUtils.canReadCode(build.getProject()) 
							&& build.getProject().getObjectId(build.getRefName(), false) != null) {
						if (build.getRequest() != null) {
							Fragment fragment = new Fragment(componentId, "linkFrag", BuildListPanel.this);
							PageParameters params = PullRequestActivitiesPage.paramsOf(build.getRequest());
							Link<Void> link = new BookmarkablePageLink<Void>("link", PullRequestActivitiesPage.class, params);
							link.add(new Label("label", MessageFormat.format(_T("pull request #{0}"), build.getRequest().getNumber())));
							fragment.add(link);
							cellItem.add(fragment);
						} else if (build.getBranch() != null) {
							Fragment fragment = new Fragment(componentId, "linkFrag", BuildListPanel.this);
							var revision = build.getBranch();
							if (build.getProject().getTagRef(revision) != null)
								revision = GitUtils.branch2ref(revision);
							PageParameters params = ProjectBlobPage.paramsOf(build.getProject(),
									new BlobIdent(revision, null, FileMode.TREE.getBits()));
							Link<Void> link = new BookmarkablePageLink<Void>("link", ProjectBlobPage.class, params);
							link.add(new Label("label", MessageFormat.format(_T("branch {0}"), build.getBranch())));
							fragment.add(link);
							cellItem.add(fragment);
						} else if (build.getTag() != null) {
							Fragment fragment = new Fragment(componentId, "linkFrag", BuildListPanel.this);
							var revision = build.getTag();
							if (build.getProject().getBranchRef(revision) != null)
								revision = GitUtils.tag2ref(revision);
							PageParameters params = ProjectBlobPage.paramsOf(build.getProject(),
									new BlobIdent(revision, null, FileMode.TREE.getBits()));
							Link<Void> link = new BookmarkablePageLink<Void>("link", ProjectBlobPage.class, params);
							link.add(new Label("label", MessageFormat.format(_T("tag {0}"), build.getTag())));
							fragment.add(link);
							cellItem.add(fragment);
						} else if (build.getRefName().equals(REF_TOD)) {
							cellItem.add(new Label(componentId, "tod").setEscapeModelStrings(false));
						} else {
							cellItem.add(new Label(componentId, "<i>n/a</i>").setEscapeModelStrings(false));
						}
					} else {
						if (build.getRequest() != null)
							cellItem.add(new Label(componentId, MessageFormat.format(_T("pull request #{0}"), build.getRequest().getNumber())));
						else if (build.getBranch() != null)
							cellItem.add(new Label(componentId, MessageFormat.format(_T("branch {0}"), build.getBranch())));
						else if (build.getTag() != null)
							cellItem.add(new Label(componentId, MessageFormat.format(_T("tag {0}"), build.getTag())));
						else if (build.getRefName().equals(REF_TOD))
							cellItem.add(new Label(componentId, "tod"));
						else
							cellItem.add(new Label(componentId, "<i>" + _T("n/a") + "</i>").setEscapeModelStrings(false));
					}
				}
			});
		}
		
		for (String paramName: getListParams()) {
			columns.add(new AbstractColumn<>(Model.of(paramName)) {

				@Override
				public String getCssClass() {
					return "param d-none d-xl-table-cell text-break";
				}

				@Override
				public void populateItem(Item<ICellPopulator<Build>> cellItem, String componentId, IModel<Build> rowModel) {
					Build build = rowModel.getObject();
					Input param = build.getParamInputs().get(paramName);
					if (param != null && build.isParamVisible(paramName))
						cellItem.add(new ParamValuesLabel(componentId, param));
					else
						cellItem.add(new Label(componentId, "<i>" + _T("Unspecified") + "</i>").setEscapeModelStrings(false));
				}

			});
		}

		if (showDuration) {
			columns.add(new AbstractColumn<>(Model.of(_T("Duration"))) {

				@Override
				public String getCssClass() {
					return "d-none d-xl-table-cell";
				}

				@Override
				public void populateItem(Item<ICellPopulator<Build>> cellItem, String componentId, IModel<Build> rowModel) {
					Build build = rowModel.getObject();
					if (build.getRunningDate() != null) {
						long duration;
						if (build.getFinishDate() != null)
							duration = build.getFinishDate().getTime() - build.getRunningDate().getTime();
						else
							duration = System.currentTimeMillis() - build.getRunningDate().getTime();
						if (duration < 0)
							duration = 0;
						cellItem.add(new Label(componentId, DateUtils.formatDuration(duration)));
					} else {
						cellItem.add(new Label(componentId, "<i>n/a</i>").setEscapeModelStrings(false));
					}
				}
			});
		}

		columns.add(new AbstractColumn<>(Model.of(_T("Last Update"))) {

			@Override
			public String getCssClass() {
				return "date d-none d-xl-table-cell";
			}

			@Override
			public void populateItem(Item<ICellPopulator<Build>> cellItem, String componentId, IModel<Build> rowModel) {
				Build build = rowModel.getObject();
				Long buildId = build.getId();

				Fragment fragment = new Fragment(componentId, "dateFrag", BuildListPanel.this);
				fragment.add(new Label("name", new AbstractReadOnlyModel<String>() {

					@Override
					public String getObject() {
						return _T(rowModel.getObject().getStatus().toString());
					}

				}) {

					@Override
					protected void onInitialize() {
						super.onInitialize();
						add(newBuildObserver(buildId));
						setOutputMarkupId(true);
					}

				});
				fragment.add(new Label("date", new LoadableDetachableModel<String>() {

					@Override
					protected String load() {
						return DateUtils.formatAge(rowModel.getObject().getStatusDate());
					}

				}));
				fragment.add(newBuildObserver(buildId));
				fragment.setOutputMarkupId(true);
				cellItem.add(fragment);
			}
		});		
		
		body.add(buildsTable = new DefaultDataTable<>("builds", columns, dataProvider,
				WebConstants.PAGE_SIZE, getPagingHistorySupport()));
		
		setOutputMarkupId(true);
	}
	
	private void newRevisionSelector(AjaxRequestTarget target, Project project) {
		var projectId = project.getId();
		
		var placement = new AlignPlacement(100, 100, 100, 0, 0);
		Alignment alignment = new Alignment(new ComponentTarget(runJobLink), placement);
		new FloatingPanel(target, alignment, true, true, null) {

			private Project getRevisionProject() {
				return getProjectManager().load(projectId);
			}
			
			@Override
			protected Component newContent(String id) {
				return new RevisionSelector(id, new LoadableDetachableModel<>() {
					@Override
					protected Project load() {
						return getRevisionProject(); 
					}
				}, null, false) {
					
					@Override
					protected String getTitle() {
						return _T("Select Branch/Tag");
					}

					@Override
					protected void onSelect(AjaxRequestTarget target, String revision) {
						close();
						
						new FloatingPanel(target, alignment, true, true, null) {

							@Override
							protected Component newContent(String id) {
								return new JobRunSelector(id, revision) {
									@Override
									protected void onSelect(AjaxRequestTarget target, String jobName) {
										close();
									}

									@Override
									protected Project getProject() {
										return getRevisionProject();
									}
								};
							}
							
						};
					}
				};
			}
			
		};
	}
	
	private List<String> getListParams() {
		if (getProject() != null)
			return getProject().getBuildSetting().getListParams(true);
		else
			return getGlobalBuildSetting().getListParams();
	}
	
	private ChangeObserver newBuildObserver(Long buildId) {
		return new ChangeObserver() {
			
			@Override
			public Collection<String> findObservables() {
				return Sets.newHashSet(Build.getDetailChangeObservable(buildId));
			}
			
		};
	}
	
}
