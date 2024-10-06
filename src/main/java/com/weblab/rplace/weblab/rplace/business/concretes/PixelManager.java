package com.weblab.rplace.weblab.rplace.business.concretes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.weblab.rplace.weblab.rplace.business.abstracts.BanService;
import com.weblab.rplace.weblab.rplace.business.abstracts.PixelLogService;
import com.weblab.rplace.weblab.rplace.business.abstracts.UserService;
import com.weblab.rplace.weblab.rplace.business.constants.Messages;
import com.weblab.rplace.weblab.rplace.core.utilities.results.*;
import com.weblab.rplace.weblab.rplace.entities.PixelLog;
import com.weblab.rplace.weblab.rplace.entities.Role;
import com.weblab.rplace.weblab.rplace.entities.User;
import com.weblab.rplace.weblab.rplace.entities.dtos.FillDto;
import com.weblab.rplace.weblab.rplace.entities.dtos.PixelDto;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.weblab.rplace.weblab.rplace.business.abstracts.PixelService;
import com.weblab.rplace.weblab.rplace.dataAccess.abstracts.PixelDao;
import com.weblab.rplace.weblab.rplace.entities.Pixel;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PixelManager implements PixelService {

	private final PixelDao pixelDao;

	private final PixelLogService pixelLogService;

	private final UserService userService;

	private final BanService banService;

	@Value("${canvas.max.pixel.x}")
	private String canvasMaxPixelX;

	@Value("${canvas.max.pixel.y}")
	private String canvasMaxPixelY;

	@Value("${school.mail.enabled}")
	private Boolean isSchoolMailEnabled;

	public PixelManager(BanService banService, PixelDao pixelDao, @Lazy PixelLogService pixelLogService, UserService userService) {
		this.banService = banService;
		this.pixelDao = pixelDao;
		this.pixelLogService = pixelLogService;
		this.userService = userService;
	}

	@Override
	public DataResult<List<Pixel>> getBoard() {
		var result =pixelDao.findAllByOrderByXAscYAsc();

		return new SuccessDataResult<List<Pixel>>(result, Messages.boardSuccessfullyBrought);
	}


	@Override
	@Cacheable(value = "colors", key = "#root.methodName", unless = "#result == null")
	public DataResult<List<String>> getColors() {

		var result = pixelDao.findAllByOrderByXAscYAsc();

		if (result == null) {
			return new ErrorDataResult<>(Messages.getPixelsUnsuccessful);
		}

		List<String> colors = result.stream().map(p -> p.getColor()).toList();

		return new SuccessDataResult<List<String>>(colors, Messages.colorsMatrisSuccessfullyBrought);
	}

	private boolean CheckIfPixelExistsByList(List<Pixel> result, int x, int y) {
		for(Pixel pixel : result){
			if(pixel.getX() == x && pixel.getY() == y){
				return true;
			}
		}
		return false;
	}



	@Override
	@CacheEvict(value = "colors", allEntries = true)
	public Result addPixel(Pixel pixel, String ipAddress) {
		var loggedInUserResult = userService.getAuthenticatedUser();

		if (!loggedInUserResult.isSuccess()) {
			return loggedInUserResult;
		}

		var loggedInUser = loggedInUserResult.getData();

		var userResult = userService.getUserBySchoolMail(loggedInUser.getSchoolMail());
		if (!userResult.isSuccess()) {
			return userResult;
		}

		if(isSchoolMailEnabled && !CheckIfSchoolMailCorrect(userResult.getData().getSchoolMail())){
			return new ErrorResult(Messages.invalidSchoolMailToAddPixel);
		}


		var ipBanResult = banService.isIpBanned(ipAddress);
		if(ipBanResult.isSuccess()){
			return new ErrorResult(ipBanResult.getMessage());
		}


		var userBanResult = banService.isUserBanned(userResult.getData().getSchoolMail());
		if(userBanResult.isSuccess()){
			return new ErrorResult(userBanResult.getMessage());
		}


		if(!CheckIfLastPlacedTimeCorrect(userResult.getData())){
			return new ErrorResult(Messages.lastPlacedTimeMustBeCorrect);
		}


		//pixel rengi doğru mu?
		if(!CheckIfColorsCorrect(pixel)){
			return new ErrorResult(Messages.colorMustBeCorrect);
		}

		if(!CheckIfPixelInsideCanvas(pixel)){
			return new ErrorResult(Messages.pixelMustBeInsideCanvas);
		}


		if(CheckIfPixelExists(pixel) == true) {
			var pixelToChange = pixelDao.findByXAndY(pixel.getX(), pixel.getY());
			String oldColor = pixelToChange.getColor();

			pixelToChange.setColor(pixel.getColor());
			pixelDao.save(pixelToChange);
			pixelLogService.addPixelLog(pixelToChange, oldColor, ipAddress);

			return new SuccessResult(Messages.pixelColorChanged);
		}

		pixelDao.save(pixel);
		pixelLogService.addPixelLog(pixel,pixel.getColor(), ipAddress);
		return new SuccessResult(Messages.pixelSuccessfullyAddedToDatabase);
	}

	private boolean CheckIfSchoolMailCorrect(String schoolMail) {
		return schoolMail.endsWith("@std.yildiz.edu.tr");
	}

	private boolean CheckIfPixelInsideCanvas(Pixel pixel) {

		if (pixel.getX() < 0 || pixel.getX() > Integer.parseInt(canvasMaxPixelX) || pixel.getY() < 0 || pixel.getY() > Integer.parseInt(canvasMaxPixelY)) {
			return false;
		}
		return true;

	}

	private boolean CheckIfLastPlacedTimeCorrect(User user) {

		if (user.getLastPlacedAt() == null) {
			return true;
		}

		if(user.getAuthorities().contains(Role.ROLE_MODERATOR) || user.getAuthorities().contains(Role.ROLE_ADMIN)){
			return true;
		}

		if(user.getLastPlacedAt().getTime() + 3000 < new Date().getTime()){
			return true;
		}
		return false;

	}

	private boolean CheckIfColorsCorrect(Pixel pixel){

		List<String> colorsToCheck = Arrays.asList(
				/*
				"0xfca5a5", "fca5a5",
				"0xfde047", "fde047",
				"0x86efac", "86efac",
				"0x93c5fd", "93c5fd",
				"0xa5b4fc", "a5b4fc",
				"0xd8b4fe", "d8b4fe",
				"0xf9a8d4", "f9a8d4",
				*/

				"0x6d001a", "6d001a",
				"0xbe0039", "be0039",
				"0xff4500", "ff4500",
				"0xffa800", "ffa800",
				"0xffd635", "ffd635",
				"0xfff8b8", "fff8b8",
				"0x00a368", "00a368","a368",
				"0x00cc78", "00cc78","cc78",
				"0x7eed56", "7eed56",
				"0x00756f", "00756f","756f",
				"0x009eaa", "009eaa","9eaa",
				"0x00ccc0", "00ccc0","ccc0",
				"0x2450a4", "2450a4",
				"0x3690ea", "3690ea",
				"0x51e9f4", "51e9f4",
				"0x493ac1", "493ac1",
				"0x6a5cff", "6a5cff",
				"0x94b3ff", "94b3ff",
				"0x811e9f", "811e9f",
				"0xb44ac0", "b44ac0",
				"0xe4abff", "e4abff",
				"0xde107f", "de107f",
				"0xff3881", "ff3881",
				"0xff99aa", "ff99aa",
				"0x6d482f", "6d482f",
				"0x9c6926", "9c6926",
				"0xffb470", "ffb470",
				"0x000000", "0",
				"0x515252", "515252",
				"0x898d90", "898d90",
				"0xd4d7d9", "d4d7d9",
				"0xffffff", "ffffff");

		String color = pixel.getColor().toLowerCase();

		return colorsToCheck.contains(color);
	}

	private boolean CheckIfPixelExists(Pixel pixel) {

		var result = pixelDao.findByXAndY(pixel.getX(), pixel.getY());

		if(result == null) {
			return false;
		}
		return true;
	}


	@Override
	public DataResult<Pixel> getByXAndY(int x, int y) {

		return new SuccessDataResult<Pixel>(pixelDao.findByXAndY(x, y),Messages.pixelSuccessfullyBrought);

	}

	@SneakyThrows
	@Override
	public DataResult<List<PixelDto>> getPixelsBetweenDates(long unixStartDate, long unixEndDate) {
		var result = pixelLogService.getPixelLogsBetweenDates(unixStartDate, unixEndDate);
		Date startDate = new Date(unixStartDate * 1000);
		Date endDate = new Date(unixEndDate * 1000);

		if (!result.isSuccess()){
			return new ErrorDataResult<>(Messages.getPixelsUnsuccessful);
		}

		List<PixelDto> pixelDtos = new ArrayList<>();
		for (PixelLog pixelLog : result.getData()) {
			var pixel = PixelDto.builder()
					.x(pixelLog.getPixel().getX())
					.y(pixelLog.getPixel().getY())
					.color(pixelLog.getPixel().getColor())
					.build();

			pixelDtos.add(pixel);
		}

		return new SuccessDataResult<List<PixelDto>>(pixelDtos, Messages.pixelSuccessfullyBrought + " Başlangıç: " + startDate + " Bitiş: " + endDate);

	}


	@Transactional
	@Override
	@CacheEvict(value = "colors", allEntries = true)
	public DataResult<FillDto> fill(FillDto fillDto, String ipAddress) {

		if(!CheckIfColorsCorrect(Pixel.builder().color(fillDto.getColor()).build())){
			return new ErrorDataResult<>(Messages.colorMustBeCorrect);
		}

		if (fillDto.getStartX() > fillDto.getEndX() || fillDto.getStartY() > fillDto.getEndY()) {
			return new ErrorDataResult<>(Messages.fillAreaMustBeCorrect);
		}

		//List<Pixel> pixels = getBoard().getData();

		//pixelLog.setPixel();

		pixelDao.updateColors(fillDto.getColor(), fillDto.getStartX(), fillDto.getEndX(), fillDto.getStartY(), fillDto.getEndY());
		pixelLogService.addPixelLogsWithQuery(fillDto.getStartX(), fillDto.getEndX(), fillDto.getStartY(), fillDto.getEndY(), ipAddress);

		//pixelDao.saveAll(pixels);

		//pixelLogService.addPixelLogs(pixelLogs);
		return new SuccessDataResult<>(fillDto, Messages.fillSuccessfullyExecuted);

	}

	@Override
	@CacheEvict(value = "colors", allEntries = true)
	public DataResult<FillDto> bringBackPreviousPixels(FillDto fillDto, String ipAddress) {

		if (fillDto.getStartX() > fillDto.getEndX() || fillDto.getStartY() > fillDto.getEndY()) {
			return new ErrorDataResult<>(Messages.fillAreaMustBeCorrect);
		}

		pixelDao.bringBackPreviousPixels(fillDto.getStartX(), fillDto.getEndX(), fillDto.getStartY(), fillDto.getEndY());

		//var newPixels = pixelDao.findAllByXBetweenAndYBetween(fillDto.getStartX(), fillDto.getEndX(), fillDto.getStartY(), fillDto.getEndY());


		return new SuccessDataResult<>(null,Messages.bringBackPreviousPixelsSuccessfullyExecuted);


	}


}
