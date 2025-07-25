package io.onedev.server.web.editable.password;

import io.onedev.server.annotation.Password;
import io.onedev.server.web.editable.*;
import org.apache.wicket.Component;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.model.IModel;

import java.lang.reflect.AnnotatedElement;

public class PasswordEditSupport implements EditSupport {

	@Override
	public PropertyContext<?> getEditContext(PropertyDescriptor descriptor) {
		if (descriptor.getPropertyClass() == String.class) {
			Password password = descriptor.getPropertyGetter().getAnnotation(Password.class);
			if (password != null) {
				if (password.checkPolicy()) {
					return new PropertyContext<String>(descriptor) {

						@Override
						public PropertyViewer renderForView(String componentId, final IModel<String> model) {
							return new PropertyViewer(componentId, descriptor) {

								@Override
								protected Component newContent(String id, PropertyDescriptor propertyDescriptor) {
									if (model.getObject() != null) {
										return new Label(id, "********");
									} else {
										return new EmptyValueLabel(id) {

											@Override
											protected AnnotatedElement getElement() {
												return propertyDescriptor.getPropertyGetter();
											}
											
										};
									}
								}
								
							};
						}

						@Override
						public PropertyEditor<String> renderForEdit(String componentId, IModel<String> model) {
							return new ConfirmativePasswordPropertyEditor(componentId, descriptor, model);
						}
						
					};
				} else {
					return new PropertyContext<String>(descriptor) {

						@Override
						public PropertyViewer renderForView(String componentId, final IModel<String> model) {
							return new PropertyViewer(componentId, descriptor) {

								@Override
								protected Component newContent(String id, PropertyDescriptor propertyDescriptor) {
									if (model.getObject() != null) {
										return new Label(id, "********");
									} else {
										return new EmptyValueLabel(id) {

											@Override
											protected AnnotatedElement getElement() {
												return propertyDescriptor.getPropertyGetter();
											}
											
										};
									}
								}
								
							};
						}

						@Override
						public PropertyEditor<String> renderForEdit(String componentId, IModel<String> model) {
							return new PasswordPropertyEditor(componentId, descriptor, model);
						}
						
					};
				}
			} else {
				return null;
			}
		} else {
			return null;
		}
	}

	@Override
	public int getPriority() {
		return DEFAULT_PRIORITY;
	}
	
}
