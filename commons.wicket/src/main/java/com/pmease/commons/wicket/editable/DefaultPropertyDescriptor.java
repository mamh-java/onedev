package com.pmease.commons.wicket.editable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.hibernate.validator.constraints.NotEmpty;

import com.pmease.commons.util.BeanUtils;

@SuppressWarnings("serial")
public class DefaultPropertyDescriptor implements PropertyDescriptor {

	private final Class<?> beanClass;
	
	private final String propertyName;
	
	private transient Method propertyGetter;
	
	private transient Method propertySetter;
	
	public DefaultPropertyDescriptor(Class<?> beanClass, String propertyName) {
		this.beanClass = beanClass;
		this.propertyName = propertyName;
	}
	
	public DefaultPropertyDescriptor(Method propertyGetter) {
		this.beanClass = propertyGetter.getDeclaringClass();
		this.propertyName = BeanUtils.getPropertyName(propertyGetter);
		this.propertyGetter = propertyGetter;
	}
	
	public DefaultPropertyDescriptor(Method propertyGetter, Method propertySetter) {
		this.beanClass = propertyGetter.getDeclaringClass();
		this.propertyName = BeanUtils.getPropertyName(propertyGetter);
		this.propertyGetter = propertyGetter;
		this.propertySetter = propertySetter;
	}

	public DefaultPropertyDescriptor(PropertyDescriptor propertyDescriptor) {
		this.beanClass = propertyDescriptor.getBeanClass();
		this.propertyName = propertyDescriptor.getPropertyName();
		this.propertyGetter = propertyDescriptor.getPropertyGetter();
		this.propertySetter = propertyDescriptor.getPropertySetter();
	}
	
	@Override
	public Class<?> getBeanClass() {
		return beanClass;
	}

	@Override
	public String getPropertyName() {
		return propertyName;
	}

	@Override
	public Method getPropertyGetter() {
		if (propertyGetter == null)
			propertyGetter = BeanUtils.getGetter(beanClass, propertyName);
		return propertyGetter;
	}
	
	public Method getPropertySetter() {
		if (propertySetter == null)
			propertySetter = BeanUtils.getSetter(getPropertyGetter());
		return propertySetter;
	}

	@Override
	public void copyProperty(Object fromBean, Object toBean) {
		setPropertyValue(toBean, getPropertyValue(fromBean));
	}

	@Override
	public Object getPropertyValue(Object bean) {
		try {
			return getPropertyGetter().invoke(bean);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void setPropertyValue(Object bean, Object propertyValue) {
		try {
			getPropertySetter().invoke(bean, propertyValue);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Class<?> getPropertyClass() {
		return getPropertyGetter().getReturnType();
	}

	@Override
	public boolean isPropertyRequired() {
		return propertyGetter.getReturnType().isPrimitive()
				|| propertyGetter.getAnnotation(NotNull.class) != null 
				|| propertyGetter.getAnnotation(NotEmpty.class) != null
				|| propertyGetter.getAnnotation(Size.class) != null && propertyGetter.getAnnotation(Size.class).min()>=1;
	}

}
